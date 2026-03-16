package com.matching.trading.service;

import com.matching.trading.config.StrategyProperties;
import com.matching.trading.model.DepthLevel;
import com.matching.trading.model.OrderBookView;
import com.matching.trading.model.SignalSnapshot;
import com.matching.trading.orderbook.LocalOrderBook;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SignalEngine {
    private static final MathContext MC = MathContext.DECIMAL64;

    private final LocalOrderBook localOrderBook;
    private final TradeSignalState tradeSignalState;
    private final StrategyProperties strategyProperties;

    public SignalEngine(
        LocalOrderBook localOrderBook,
        TradeSignalState tradeSignalState,
        StrategyProperties strategyProperties
    ) {
        this.localOrderBook = localOrderBook;
        this.tradeSignalState = tradeSignalState;
        this.strategyProperties = strategyProperties;
    }

    public SignalSnapshot snapshot() {
        OrderBookView orderBook = localOrderBook.snapshot(strategyProperties.getSignal().getTopLevels());
        if (!orderBook.synced() || orderBook.bestBid() == null || orderBook.bestAsk() == null) {
            return new SignalSnapshot(false, false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, tradeSignalState.lastTradePrice(), "order book not ready");
        }

        BigDecimal bidQty = sum(orderBook.bids());
        BigDecimal askQty = sum(orderBook.asks());
        BigDecimal imbalance = askQty.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.valueOf(99)
            : bidQty.divide(askQty, MC);
        BigDecimal aggressiveBuyRatio = tradeSignalState.aggressiveBuyRatio();
        BigDecimal spreadBps = orderBook.bestAsk()
            .subtract(orderBook.bestBid(), MC)
            .divide(orderBook.bestBid(), 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(10_000L), MC);
        BigDecimal breakoutPrice =
            tradeSignalState.breakoutReference(strategyProperties.getSignal().getBreakoutWindowSeconds() * 1000L);
        BigDecimal lastTradePrice = tradeSignalState.lastTradePrice();

        boolean breakout = lastTradePrice.compareTo(breakoutPrice) >= 0;
        boolean signal = imbalance.compareTo(strategyProperties.getSignal().getImbalanceThreshold()) >= 0
            && aggressiveBuyRatio.compareTo(strategyProperties.getSignal().getAggressiveBuyRatioThreshold()) >= 0
            && spreadBps.compareTo(strategyProperties.getSignal().getMaxSpreadBps()) <= 0
            && breakout;

        String reason = signal ? "buy pressure confirmed" : "threshold not met";
        return new SignalSnapshot(true, signal, imbalance, aggressiveBuyRatio, spreadBps, breakoutPrice,
            lastTradePrice, reason);
    }

    private BigDecimal sum(List<DepthLevel> levels) {
        BigDecimal total = BigDecimal.ZERO;
        for (DepthLevel level : levels) {
            total = total.add(level.quantity(), MC);
        }
        return total;
    }
}
