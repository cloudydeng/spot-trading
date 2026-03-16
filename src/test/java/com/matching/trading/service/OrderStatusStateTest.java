package com.matching.trading.service;

import com.matching.trading.model.ExecutionReportSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderStatusStateTest {
    @Test
    void shouldKeepLatestOrderStatusFromExecutionReport() {
        OrderStatusState state = new OrderStatusState();

        state.update(new ExecutionReportSnapshot(
            "NIGHTUSDT",
            1001L,
            "BUY",
            "MARKET",
            "TRADE",
            "PARTIALLY_FILLED",
            new BigDecimal("10"),
            new BigDecimal("4"),
            new BigDecimal("8"),
            new BigDecimal("4"),
            new BigDecimal("2"),
            100L
        ));
        state.update(new ExecutionReportSnapshot(
            "NIGHTUSDT",
            1001L,
            "BUY",
            "MARKET",
            "TRADE",
            "FILLED",
            new BigDecimal("10"),
            new BigDecimal("10"),
            new BigDecimal("20"),
            new BigDecimal("6"),
            new BigDecimal("2"),
            200L
        ));

        assertEquals("FILLED", state.get(1001L).status());
        assertEquals(1, state.latest(20).size());
    }
}
