package com.matching.trading.service;

import com.matching.trading.config.StrategyProperties;
import com.matching.trading.config.TradingProperties;
import com.matching.trading.config.BinanceProperties;
import com.matching.trading.model.ExchangeFilters;
import com.matching.trading.model.MarketOrderResult;
import com.matching.trading.model.PositionSnapshot;
import com.matching.trading.model.SignalSnapshot;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TradeEngine {
    private static final Logger log = LoggerFactory.getLogger(TradeEngine.class);

    private final SignalEngine signalEngine;
    private final BinanceRestClient restClient;
    private final BinanceProperties binanceProperties;
    private final StrategyProperties strategyProperties;
    private final TradingProperties tradingProperties;
    private final PositionState positionState;
    private final PaperTradingService paperTradingService;
    private final ExchangeRuleService exchangeRuleService;
    private final UserDataStreamService userDataStreamService;
    private final TradingAuditLogService tradingAuditLogService;
    private final TradingRiskState tradingRiskState;
    private final AtomicReference<Instant> lastOrderTime = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<Instant> lastExitTime = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<Instant> apiCooldownUntil = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<Instant> signalDetectedAt = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<String> lastSkipReason = new AtomicReference<>("");

    private volatile ExchangeFilters exchangeFilters;

    public TradeEngine(
        SignalEngine signalEngine,
        BinanceRestClient restClient,
        BinanceProperties binanceProperties,
        StrategyProperties strategyProperties,
        TradingProperties tradingProperties,
        PositionState positionState,
        PaperTradingService paperTradingService,
        ExchangeRuleService exchangeRuleService,
        UserDataStreamService userDataStreamService,
        TradingAuditLogService tradingAuditLogService,
        TradingRiskState tradingRiskState
    ) {
        this.signalEngine = signalEngine;
        this.restClient = restClient;
        this.binanceProperties = binanceProperties;
        this.strategyProperties = strategyProperties;
        this.tradingProperties = tradingProperties;
        this.positionState = positionState;
        this.paperTradingService = paperTradingService;
        this.exchangeRuleService = exchangeRuleService;
        this.userDataStreamService = userDataStreamService;
        this.tradingAuditLogService = tradingAuditLogService;
        this.tradingRiskState = tradingRiskState;
    }

    @PostConstruct
    public void init() {
        try {
            exchangeFilters = restClient.fetchExchangeFilters();
            log.info("Loaded exchange filters: tickSize={}, stepSize={}, minQty={}, minNotional={}",
                exchangeFilters.tickSize(), exchangeFilters.stepSize(), exchangeFilters.minQty(), exchangeFilters.minNotional());
        } catch (Exception e) {
            log.warn("Failed to load exchange filters during startup; strategy will stay conservative until retry", e);
        }
    }

    @Scheduled(fixedDelay = 1000L)
    public void evaluate() {
        Instant now = Instant.now();
        if (!strategyProperties.isEnabled()) {
            return;
        }

        Instant cooldownUntil = apiCooldownUntil.get();
        if (cooldownUntil.isAfter(now)) {
            logSkipReason("entry api cooldown until " + cooldownUntil);
            return;
        }

        if (exchangeFilters == null) {
            try {
                exchangeFilters = restClient.fetchExchangeFilters();
            } catch (Exception e) {
                log.debug("Exchange filters still unavailable", e);
                return;
            }
        }

        SignalSnapshot snapshot = signalEngine.snapshot();
        if (!snapshot.ready()) {
            signalDetectedAt.set(Instant.EPOCH);
            logSkipReason("entry waiting for order book readiness");
            return;
        }

        if (positionState.isOpen()) {
            signalDetectedAt.set(Instant.EPOCH);
            clearSkipReason();
            manageOpenPosition(snapshot, now);
            return;
        }

        if (tradingRiskState.isEntryPaused(now)) {
            logSkipReason("entry paused until " + tradingRiskState.pausedUntil()
                + " after " + tradingRiskState.consecutiveLosses() + " consecutive losses");
            return;
        }

        if (!snapshot.buyPressureDetected()) {
            signalDetectedAt.set(Instant.EPOCH);
            logSkipReason("entry signal not triggered: " + snapshot.reason());
            return;
        }

        if (!meetsMinimumBreakoutMove(snapshot)) {
            signalDetectedAt.set(Instant.EPOCH);
            logSkipReason("entry waiting for min breakout move: lastTradePrice="
                + snapshot.lastTradePrice() + ", breakoutPrice=" + snapshot.breakoutPrice());
            return;
        }

        if (!signalHeldLongEnough(now)) {
            logSkipReason("entry waiting for signal hold: detectedAt=" + signalDetectedAt.get()
                + ", minHoldMs=" + strategyProperties.getSignal().getMinSignalHoldMs());
            return;
        }

        Instant nextEntryTime = lastOrderTime.get().plusSeconds(strategyProperties.getExecution().getCooldownSeconds());
        if (nextEntryTime.isAfter(now)) {
            logSkipReason("entry cooldown active until " + nextEntryTime);
            return;
        }

        BigDecimal quoteAmount = effectiveQuoteAmount();
        if (quoteAmount == null || quoteAmount.compareTo(BigDecimal.ZERO) <= 0) {
            logSkipReason("entry skipped because effective quote amount is not positive");
            return;
        }
        if (!tradingRiskState.canSubmitEntryOrder(now, strategyProperties.getExecution().getMaxOrdersPerMinute())) {
            logSkipReason("entry skipped because maxOrdersPerMinute="
                + strategyProperties.getExecution().getMaxOrdersPerMinute()
                + " was reached; recentEntryOrders=" + tradingRiskState.recentEntryOrderCount(now));
            return;
        }
        if (exchangeFilters != null
            && exchangeFilters.minNotional() != null
            && quoteAmount.compareTo(exchangeFilters.minNotional()) < 0) {
            logSkipReason("entry quote amount " + quoteAmount
                + " is below exchange min notional " + exchangeFilters.minNotional());
            return;
        }
        BigDecimal estimatedBuyQty = exchangeRuleService.normalizeQuantity(
            exchangeRuleService.estimateBaseQuantityFromQuote(quoteAmount, snapshot.lastTradePrice()),
            exchangeFilters
        );
        if (!exchangeRuleService.isSellQuantityValid(estimatedBuyQty, snapshot.lastTradePrice(), exchangeFilters)) {
            logSkipReason("entry skipped because estimated quantity " + estimatedBuyQty
                + " cannot satisfy later sell rules at price " + snapshot.lastTradePrice());
            return;
        }
        clearSkipReason();
        tradingAuditLogService.signal("buy_signal", snapshot);

        if (runtimeMode() == TradingProperties.StartupMode.DRY_RUN) {
            log.info("DRY_RUN buy signal triggered: {}", snapshot);
            lastOrderTime.set(now);
            signalDetectedAt.set(Instant.EPOCH);
            return;
        }

        try {
            tradingRiskState.recordEntryOrder(now);
            MarketOrderResult response = runtimeMode() == TradingProperties.StartupMode.PAPER
                ? paperTradingService.simulateBuy(binanceProperties.getSymbol(), quoteAmount, snapshot.lastTradePrice())
                : restClient.placeMarketBuy(quoteAmount);
            log.info("Placed BUY order for {} USDT in mode {}: {}", quoteAmount, runtimeMode(), response);
            tradingAuditLogService.order("buy_order", response);
            if (response.executedQty().compareTo(BigDecimal.ZERO) > 0) {
                positionState.open(response.executedQty(), response.cumulativeQuoteQty(), response.averagePrice(), now);
            }
            lastOrderTime.set(now);
            signalDetectedAt.set(Instant.EPOCH);
        } catch (BinanceApiException e) {
            applyApiCooldownIfNeeded(e);
            log.warn("BUY order rejected: {}", e.getMessage());
        }
    }

    private void manageOpenPosition(SignalSnapshot snapshot, Instant now) {
        PositionSnapshot position = positionState.snapshot(snapshot.lastTradePrice());
        if (!position.open() || position.lastPrice() == null || position.lastPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        if (lastExitTime.get().plusSeconds(strategyProperties.getExecution().getMinExitIntervalSeconds()).isAfter(now)) {
            return;
        }

        if (runtimeMode() == TradingProperties.StartupMode.LIVE && positionState.hasPendingSellOrder()) {
            return;
        }

        BigDecimal pnlRate = position.unrealizedPnlRate();
        boolean takeProfit = pnlRate.compareTo(strategyProperties.getRisk().getTakeProfitRate()) >= 0;
        boolean stopLoss = pnlRate.compareTo(strategyProperties.getRisk().getStopLossRate().negate()) <= 0;
        boolean timeout = position.openedAt() != null
            && ChronoUnit.SECONDS.between(position.openedAt(), now) >= strategyProperties.getRisk().getMaxHoldingSeconds();

        if (!takeProfit && !stopLoss && !timeout) {
            return;
        }

        String reason = takeProfit ? "TAKE_PROFIT" : stopLoss ? "STOP_LOSS" : "TIMEOUT";
        BigDecimal sellQty = exchangeRuleService.normalizeQuantity(position.quantity(), exchangeFilters);
        if (!exchangeRuleService.isSellQuantityValid(sellQty, position.lastPrice(), exchangeFilters)) {
            log.warn("Sell skipped [{}]: normalized quantity {} does not satisfy exchange filters {}", reason, sellQty, exchangeFilters);
            lastExitTime.set(now);
            return;
        }
        tradingAuditLogService.signal("sell_signal_" + reason.toLowerCase(), snapshot);
        if (runtimeMode() == TradingProperties.StartupMode.DRY_RUN) {
            log.info("DRY_RUN sell signal triggered [{}]: {}", reason, position);
            lastExitTime.set(now);
            return;
        }

        try {
            MarketOrderResult response = runtimeMode() == TradingProperties.StartupMode.PAPER
                ? paperTradingService.simulateSell(binanceProperties.getSymbol(), sellQty, position.lastPrice())
                : restClient.placeMarketSell(sellQty);
            log.info("Placed SELL order [{}] in mode {}: {}", reason, runtimeMode(), response);
            tradingAuditLogService.order("sell_order_" + reason.toLowerCase(), response);
            if (runtimeMode() == TradingProperties.StartupMode.PAPER) {
                positionState.reduce(response.executedQty());
                boolean closedResidualDust = false;
                if (positionState.isOpen()) {
                    PositionSnapshot residual = positionState.snapshot(position.lastPrice());
                    BigDecimal residualSellQty = exchangeRuleService.normalizeQuantity(residual.quantity(), exchangeFilters);
                    if (residual.quantity().compareTo(BigDecimal.ZERO) > 0
                        && residualSellQty != null
                        && residualSellQty.compareTo(BigDecimal.ZERO) == 0) {
                        log.info("Closing residual dust position after PAPER sell [{}]: quantity={}, filters={}",
                            reason, residual.quantity(), exchangeFilters);
                        positionState.close();
                        closedResidualDust = true;
                    }
                }
                if (!positionState.isOpen() || closedResidualDust) {
                    tradingRiskState.recordClosedPosition(
                        position.notional(),
                        response.cumulativeQuoteQty(),
                        strategyProperties.getRisk(),
                        now
                    );
                }
            } else {
                positionState.registerSellOrder(response.orderId());
            }
            lastExitTime.set(now);
        } catch (BinanceApiException e) {
            applyApiCooldownIfNeeded(e);
            log.warn("SELL order rejected: {}", e.getMessage());
        }
    }

    private TradingProperties.StartupMode runtimeMode() {
        if (strategyProperties.isLiveTrading()) {
            return TradingProperties.StartupMode.LIVE;
        }
        return tradingProperties.getStartupMode();
    }

    private void applyApiCooldownIfNeeded(BinanceApiException e) {
        if (e.getError().retryable()) {
            apiCooldownUntil.set(Instant.now().plusSeconds(tradingProperties.getApi().getRetryCooldownSeconds()));
        }
    }

    private boolean signalHeldLongEnough(Instant now) {
        long minSignalHoldMs = strategyProperties.getSignal().getMinSignalHoldMs();
        if (minSignalHoldMs <= 0) {
            return true;
        }
        Instant detectedAt = signalDetectedAt.get();
        if (Instant.EPOCH.equals(detectedAt)) {
            signalDetectedAt.set(now);
            return false;
        }
        return !detectedAt.plusMillis(minSignalHoldMs).isAfter(now);
    }

    private boolean meetsMinimumBreakoutMove(SignalSnapshot snapshot) {
        if (exchangeFilters == null || exchangeFilters.tickSize() == null
            || exchangeFilters.tickSize().compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        int minPriceMoveTicks = strategyProperties.getSignal().getMinPriceMoveTicks();
        if (minPriceMoveTicks <= 0) {
            return true;
        }
        BigDecimal requiredMove = exchangeFilters.tickSize().multiply(BigDecimal.valueOf(minPriceMoveTicks));
        BigDecimal actualMove = snapshot.lastTradePrice().subtract(snapshot.breakoutPrice());
        return actualMove.compareTo(requiredMove) >= 0;
    }

    private BigDecimal effectiveQuoteAmount() {
        BigDecimal quoteAmount = strategyProperties.getExecution().getQuoteAmount();
        BigDecimal maxPositionUsdt = strategyProperties.getExecution().getMaxPositionUsdt();
        if (quoteAmount == null) {
            return null;
        }
        if (maxPositionUsdt != null && maxPositionUsdt.compareTo(BigDecimal.ZERO) > 0
            && quoteAmount.compareTo(maxPositionUsdt) > 0) {
            return maxPositionUsdt;
        }
        return quoteAmount;
    }

    private void logSkipReason(String reason) {
        String previousReason = lastSkipReason.getAndSet(reason);
        if (!reason.equals(previousReason)) {
            log.info("Entry blocked: {}", reason);
        }
    }

    private void clearSkipReason() {
        lastSkipReason.set("");
    }
}
