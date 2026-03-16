package com.matching.trading.service;

import com.matching.trading.model.ExecutionReportSnapshot;
import com.matching.trading.model.PositionSnapshot;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PositionState {
    private static final MathContext MC = MathContext.DECIMAL64;

    private boolean open;
    private BigDecimal quantity = BigDecimal.ZERO;
    private BigDecimal entryPrice = BigDecimal.ZERO;
    private BigDecimal notional = BigDecimal.ZERO;
    private Instant openedAt;
    private final Map<Long, BigDecimal> sellOrderBaseQuantities = new ConcurrentHashMap<>();

    public synchronized boolean isOpen() {
        return open;
    }

    public synchronized void open(BigDecimal quantity, BigDecimal notional, BigDecimal entryPrice, Instant openedAt) {
        this.open = true;
        this.quantity = quantity;
        this.notional = notional;
        this.entryPrice = entryPrice;
        this.openedAt = openedAt;
    }

    public synchronized void close() {
        this.open = false;
        this.quantity = BigDecimal.ZERO;
        this.notional = BigDecimal.ZERO;
        this.entryPrice = BigDecimal.ZERO;
        this.openedAt = null;
        sellOrderBaseQuantities.clear();
    }

    public synchronized BigDecimal quantity() {
        return quantity;
    }

    public synchronized Instant openedAt() {
        return openedAt;
    }

    public synchronized boolean hasPendingSellOrder() {
        return !sellOrderBaseQuantities.isEmpty();
    }

    public synchronized void registerSellOrder(long orderId) {
        if (orderId > 0 && open) {
            sellOrderBaseQuantities.put(orderId, quantity);
        }
    }

    public synchronized void reduce(BigDecimal filledQuantity) {
        if (filledQuantity == null || filledQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        quantity = quantity.subtract(filledQuantity, MC).max(BigDecimal.ZERO);
        notional = entryPrice.multiply(quantity, MC);
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            close();
        }
    }

    public synchronized ClosedPosition applyExecutionReport(ExecutionReportSnapshot report) {
        if (report == null || report.cumulativeFilledQuantity() == null) {
            return null;
        }

        if ("BUY".equalsIgnoreCase(report.side())
            && report.cumulativeFilledQuantity().compareTo(BigDecimal.ZERO) > 0
            && ("PARTIALLY_FILLED".equals(report.orderStatus())
                || "FILLED".equals(report.orderStatus())
                || "CANCELED".equals(report.orderStatus())
                || "EXPIRED".equals(report.orderStatus()))) {
            BigDecimal avgPrice = report.cumulativeFilledQuantity().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : report.cumulativeQuoteQuantity().divide(report.cumulativeFilledQuantity(), MC);
            open(report.cumulativeFilledQuantity(), report.cumulativeQuoteQuantity(), avgPrice, Instant.ofEpochMilli(report.eventTime()));
            return null;
        }

        if ("SELL".equalsIgnoreCase(report.side())) {
            BigDecimal baseQuantity = sellOrderBaseQuantities.get(report.orderId());
            if (baseQuantity != null && ("FILLED".equals(report.orderStatus())
                || "PARTIALLY_FILLED".equals(report.orderStatus())
                || "CANCELED".equals(report.orderStatus())
                || "EXPIRED".equals(report.orderStatus())) && open) {
                BigDecimal entryNotionalBeforeClose = notional;
                if ("FILLED".equals(report.orderStatus())
                    && report.cumulativeFilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    close();
                    return new ClosedPosition(entryNotionalBeforeClose, report.cumulativeQuoteQuantity());
                }
                BigDecimal remaining = baseQuantity.subtract(report.cumulativeFilledQuantity(), MC).max(BigDecimal.ZERO);
                quantity = remaining;
                notional = entryPrice.multiply(remaining, MC);
                if (!"PARTIALLY_FILLED".equals(report.orderStatus())) {
                    sellOrderBaseQuantities.remove(report.orderId());
                }
                return null;
            }
            if ("REJECTED".equals(report.orderStatus()) && open) {
                sellOrderBaseQuantities.remove(report.orderId());
                return null;
            }
        }

        return null;
    }

    public synchronized PositionSnapshot snapshot(BigDecimal lastPrice) {
        BigDecimal pnlRate = BigDecimal.ZERO;
        if (open && entryPrice.compareTo(BigDecimal.ZERO) > 0 && lastPrice != null && lastPrice.compareTo(BigDecimal.ZERO) > 0) {
            pnlRate = lastPrice.subtract(entryPrice, MC).divide(entryPrice, MC);
        }
        return new PositionSnapshot(open, quantity, entryPrice, notional, openedAt, lastPrice, pnlRate);
    }

    public record ClosedPosition(BigDecimal entryNotional, BigDecimal exitNotional) {
    }
}
