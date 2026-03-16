package com.matching.trading.service;

import com.matching.trading.config.StrategyProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.stereotype.Component;

@Component
public class TradingRiskState {
    private static final Duration ORDER_RATE_WINDOW = Duration.ofMinutes(1);

    private final Deque<Instant> recentEntryOrders = new ArrayDeque<>();
    private Instant pausedUntil = Instant.EPOCH;
    private int consecutiveLosses;

    public synchronized boolean canSubmitEntryOrder(Instant now, int maxOrdersPerMinute) {
        evictExpiredOrders(now);
        return maxOrdersPerMinute <= 0 || recentEntryOrders.size() < maxOrdersPerMinute;
    }

    public synchronized void recordEntryOrder(Instant now) {
        evictExpiredOrders(now);
        recentEntryOrders.addLast(now);
    }

    public synchronized boolean isEntryPaused(Instant now) {
        return pausedUntil.isAfter(now);
    }

    public synchronized Instant pausedUntil() {
        return pausedUntil;
    }

    public synchronized int consecutiveLosses() {
        return consecutiveLosses;
    }

    public synchronized int recentEntryOrderCount(Instant now) {
        evictExpiredOrders(now);
        return recentEntryOrders.size();
    }

    public synchronized void recordClosedPosition(
        BigDecimal entryNotional,
        BigDecimal exitNotional,
        StrategyProperties.Risk risk,
        Instant now
    ) {
        if (entryNotional == null || exitNotional == null || risk == null
            || entryNotional.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        if (exitNotional.compareTo(entryNotional) < 0) {
            consecutiveLosses++;
            if (risk.getMaxConsecutiveLosses() > 0
                && consecutiveLosses >= risk.getMaxConsecutiveLosses()
                && risk.getPauseMinutesAfterLossLimit() > 0) {
                pausedUntil = now.plus(Duration.ofMinutes(risk.getPauseMinutesAfterLossLimit()));
            }
            return;
        }

        consecutiveLosses = 0;
        pausedUntil = Instant.EPOCH;
    }

    private void evictExpiredOrders(Instant now) {
        while (!recentEntryOrders.isEmpty()
            && recentEntryOrders.peekFirst().plus(ORDER_RATE_WINDOW).isBefore(now)) {
            recentEntryOrders.removeFirst();
        }
    }
}
