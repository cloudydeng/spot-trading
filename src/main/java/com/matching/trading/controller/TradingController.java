package com.matching.trading.controller;

import com.matching.trading.config.BinanceProperties;
import com.matching.trading.config.StrategyProperties;
import com.matching.trading.config.TradingProperties;
import com.matching.trading.model.OrderBookView;
import com.matching.trading.model.OrderStatusSnapshot;
import com.matching.trading.model.PositionSnapshot;
import com.matching.trading.model.SignalSnapshot;
import com.matching.trading.model.ExecutionReportSnapshot;
import com.matching.trading.orderbook.LocalOrderBook;
import com.matching.trading.service.OrderBookSyncService;
import com.matching.trading.service.PositionState;
import com.matching.trading.service.SignalEngine;
import com.matching.trading.service.UserDataStreamService;
import com.matching.trading.service.ExecutionReportState;
import com.matching.trading.service.OrderStatusState;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class TradingController {
    private final BinanceProperties binanceProperties;
    private final LocalOrderBook localOrderBook;
    private final SignalEngine signalEngine;
    private final OrderBookSyncService orderBookSyncService;
    private final PositionState positionState;
    private final StrategyProperties strategyProperties;
    private final TradingProperties tradingProperties;
    private final UserDataStreamService userDataStreamService;
    private final ExecutionReportState executionReportState;
    private final OrderStatusState orderStatusState;

    public TradingController(
        BinanceProperties binanceProperties,
        LocalOrderBook localOrderBook,
        SignalEngine signalEngine,
        OrderBookSyncService orderBookSyncService,
        PositionState positionState,
        StrategyProperties strategyProperties,
        TradingProperties tradingProperties,
        UserDataStreamService userDataStreamService,
        ExecutionReportState executionReportState,
        OrderStatusState orderStatusState
    ) {
        this.binanceProperties = binanceProperties;
        this.localOrderBook = localOrderBook;
        this.signalEngine = signalEngine;
        this.orderBookSyncService = orderBookSyncService;
        this.positionState = positionState;
        this.strategyProperties = strategyProperties;
        this.tradingProperties = tradingProperties;
        this.userDataStreamService = userDataStreamService;
        this.executionReportState = executionReportState;
        this.orderStatusState = orderStatusState;
    }

    @GetMapping("/")
    public Map<String, Object> home() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "spot-trading");
        response.put("symbol", binanceProperties.getSymbol());
        response.put("endpoints", new String[]{"/health", "/debug/orderbook", "/debug/signal", "/debug/rules"});
        return response;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", orderBookSyncService.isSynced() ? "UP" : "DEGRADED");
        response.put("symbol", binanceProperties.getSymbol());
        response.put("orderBookSynced", orderBookSyncService.isSynced());
        response.put("lastUpdateId", localOrderBook.getLastUpdateId());
        response.put("lastEventTime", localOrderBook.getLastEventTime());
        response.put("positionOpen", positionState.isOpen());
        response.put("mode", tradingProperties.getStartupMode().name());
        response.put("userDataStreamConnected", userDataStreamService.isConnected());
        return response;
    }

    @GetMapping("/debug/orderbook")
    public OrderBookView orderBook() {
        return localOrderBook.snapshot(10);
    }

    @GetMapping("/debug/signal")
    public SignalSnapshot signal() {
        return signalEngine.snapshot();
    }

    @GetMapping("/debug/position")
    public PositionSnapshot position() {
        return positionState.snapshot(signalEngine.snapshot().lastTradePrice());
    }

    @GetMapping("/debug/execution-report")
    public ExecutionReportSnapshot executionReport() {
        return userDataStreamService.lastExecutionReport();
    }

    @GetMapping("/debug/execution-reports")
    public List<ExecutionReportSnapshot> executionReports() {
        return executionReportState.latest(20);
    }

    @GetMapping("/debug/orders")
    public List<OrderStatusSnapshot> orders() {
        return orderStatusState.latest(20);
    }

    @GetMapping("/debug/rules")
    public Map<String, Object> rules() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("symbol", binanceProperties.getSymbol());
        response.put("runtime", runtimeRules());
        response.put("entry", entryRules());
        response.put("exit", exitRules());
        response.put("risk", riskRules());
        return response;
    }

    private Map<String, Object> runtimeRules() {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("strategyEnabled", strategyProperties.isEnabled());
        runtime.put("startupMode", tradingProperties.getStartupMode().name());
        runtime.put("liveTradingOverride", strategyProperties.isLiveTrading());
        runtime.put("effectiveMode", strategyProperties.isLiveTrading()
            ? TradingProperties.StartupMode.LIVE.name()
            : tradingProperties.getStartupMode().name());
        runtime.put("orderBookRequired", true);
        runtime.put("exchangeFiltersRequired", true);
        return runtime;
    }

    private Map<String, Object> entryRules() {
        Map<String, Object> entry = new LinkedHashMap<>();

        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("requiredMatchedConditions", strategyProperties.getSignal().getMinConditionsToTrigger());
        conditions.put("topLevels", strategyProperties.getSignal().getTopLevels());
        conditions.put("imbalanceThreshold", strategyProperties.getSignal().getImbalanceThreshold());
        conditions.put("aggressiveBuyRatioThreshold", strategyProperties.getSignal().getAggressiveBuyRatioThreshold());
        conditions.put("maxSpreadBps", strategyProperties.getSignal().getMaxSpreadBps());
        conditions.put("breakoutWindowSeconds", strategyProperties.getSignal().getBreakoutWindowSeconds());
        conditions.put("minPriceMoveTicks", strategyProperties.getSignal().getMinPriceMoveTicks());
        conditions.put("minSignalHoldMs", strategyProperties.getSignal().getMinSignalHoldMs());

        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("quoteAmount", strategyProperties.getExecution().getQuoteAmount());
        execution.put("maxPositionUsdt", strategyProperties.getExecution().getMaxPositionUsdt());
        execution.put("cooldownSeconds", strategyProperties.getExecution().getCooldownSeconds());
        execution.put("maxOrdersPerMinute", strategyProperties.getExecution().getMaxOrdersPerMinute());
        execution.put("requiresExchangeMinNotional", true);
        execution.put("requiresSellableEstimatedQuantity", true);

        entry.put("conditions", conditions);
        entry.put("execution", execution);
        return entry;
    }

    private Map<String, Object> exitRules() {
        Map<String, Object> exit = new LinkedHashMap<>();
        exit.put("takeProfitRate", strategyProperties.getRisk().getTakeProfitRate());
        exit.put("stopLossRate", strategyProperties.getRisk().getStopLossRate());
        exit.put("maxHoldingSeconds", strategyProperties.getRisk().getMaxHoldingSeconds());
        exit.put("minExitIntervalSeconds", strategyProperties.getExecution().getMinExitIntervalSeconds());
        exit.put("liveModeBlocksDuplicatePendingSellOrders", true);
        exit.put("requiresExchangeSellFilterValidation", true);
        return exit;
    }

    private Map<String, Object> riskRules() {
        Map<String, Object> risk = new LinkedHashMap<>();
        risk.put("apiRetryCooldownSeconds", tradingProperties.getApi().getRetryCooldownSeconds());
        risk.put("maxConsecutiveLosses", strategyProperties.getRisk().getMaxConsecutiveLosses());
        risk.put("pauseMinutesAfterLossLimit", strategyProperties.getRisk().getPauseMinutesAfterLossLimit());
        risk.put("marketStreamReconnectDelayMs", binanceProperties.getMarket().getWsReconnectDelayMs());
        risk.put("marketStreamStaleTimeoutMs", binanceProperties.getMarket().getStaleTimeoutMs());
        risk.put("marketStreamMaxReconnectDelayMs", binanceProperties.getMarket().getMaxReconnectDelayMs());
        risk.put("restTimeoutMs", binanceProperties.getMarket().getRestTimeoutMs());
        return risk;
    }
}
