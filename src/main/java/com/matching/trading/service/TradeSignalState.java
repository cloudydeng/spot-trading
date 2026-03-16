package com.matching.trading.service;

import com.matching.trading.config.StrategyProperties;
import com.matching.trading.model.TradeTick;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.stereotype.Component;

@Component
public class TradeSignalState {
    private static final long MIN_WINDOW_MS = 10_000L;
    private static final MathContext MC = MathContext.DECIMAL64;

    private final StrategyProperties strategyProperties;
    private final Deque<TradeTick> recentTrades = new ArrayDeque<>();
    private BigDecimal aggressiveBuyNotional = BigDecimal.ZERO;
    private BigDecimal aggressiveSellNotional = BigDecimal.ZERO;
    private BigDecimal lastTradePrice = BigDecimal.ZERO;

    public TradeSignalState(StrategyProperties strategyProperties) {
        this.strategyProperties = strategyProperties;
    }

    public synchronized void onTrade(TradeTick tick) {
        recentTrades.addLast(tick);
        lastTradePrice = tick.price();
        if (tick.buyerIsMaker()) {
            aggressiveSellNotional = aggressiveSellNotional.add(tick.notional(), MC);
        } else {
            aggressiveBuyNotional = aggressiveBuyNotional.add(tick.notional(), MC);
        }
        evictExpired(tick.eventTime());
    }

    public synchronized BigDecimal aggressiveBuyRatio() {
        if (aggressiveSellNotional.compareTo(BigDecimal.ZERO) == 0) {
            return aggressiveBuyNotional.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(99)
                : BigDecimal.ZERO;
        }
        return aggressiveBuyNotional.divide(aggressiveSellNotional, MC);
    }

    public synchronized BigDecimal breakoutReference(long breakoutWindowMs) {
        long threshold = System.currentTimeMillis() - breakoutWindowMs;
        BigDecimal max = BigDecimal.ZERO;
        for (TradeTick tick : recentTrades) {
            if (tick.eventTime() < threshold) {
                continue;
            }
            if (tick.price().compareTo(max) > 0) {
                max = tick.price();
            }
        }
        return max;
    }

    public synchronized BigDecimal lastTradePrice() {
        return lastTradePrice;
    }

    private void evictExpired(long now) {
        long retentionWindowMs = Math.max(
            MIN_WINDOW_MS,
            Math.max(
                strategyProperties.getSignal().getBreakoutWindowSeconds() * 1000L,
                strategyProperties.getSignal().getMinSignalHoldMs()
            )
        );
        while (!recentTrades.isEmpty() && now - recentTrades.peekFirst().eventTime() > retentionWindowMs) {
            TradeTick expired = recentTrades.removeFirst();
            if (expired.buyerIsMaker()) {
                aggressiveSellNotional = aggressiveSellNotional.subtract(expired.notional(), MC);
            } else {
                aggressiveBuyNotional = aggressiveBuyNotional.subtract(expired.notional(), MC);
            }
        }
    }
}
