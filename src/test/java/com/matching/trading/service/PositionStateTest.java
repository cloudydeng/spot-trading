package com.matching.trading.service;

import com.matching.trading.model.ExecutionReportSnapshot;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionStateTest {
    @Test
    void shouldOpenPositionFromFilledBuyExecutionReport() {
        PositionState positionState = new PositionState();

        positionState.applyExecutionReport(new ExecutionReportSnapshot(
            "NIGHTUSDT",
            1L,
            "BUY",
            "MARKET",
            "TRADE",
            "FILLED",
            new BigDecimal("10"),
            new BigDecimal("10"),
            new BigDecimal("20"),
            new BigDecimal("10"),
            new BigDecimal("2"),
            System.currentTimeMillis()
        ));

        assertTrue(positionState.isOpen());
        assertEquals(new BigDecimal("10"), positionState.quantity());
    }

    @Test
    void shouldClosePositionFromFilledSellExecutionReport() {
        PositionState positionState = new PositionState();
        positionState.open(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("2"), java.time.Instant.now());
        positionState.registerSellOrder(2L);

        positionState.applyExecutionReport(new ExecutionReportSnapshot(
            "NIGHTUSDT",
            2L,
            "SELL",
            "MARKET",
            "TRADE",
            "FILLED",
            new BigDecimal("10"),
            new BigDecimal("10"),
            new BigDecimal("21"),
            new BigDecimal("10"),
            new BigDecimal("2.1"),
            System.currentTimeMillis()
        ));

        assertFalse(positionState.isOpen());
        assertEquals(BigDecimal.ZERO, positionState.quantity());
    }

    @Test
    void shouldTrackPendingSellUntilOrderBecomesTerminal() {
        PositionState positionState = new PositionState();
        positionState.open(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("2"), java.time.Instant.now());
        positionState.registerSellOrder(2L);

        assertTrue(positionState.hasPendingSellOrder());

        positionState.applyExecutionReport(new ExecutionReportSnapshot(
            "NIGHTUSDT",
            2L,
            "SELL",
            "MARKET",
            "TRADE",
            "FILLED",
            new BigDecimal("10"),
            new BigDecimal("10"),
            new BigDecimal("21"),
            new BigDecimal("10"),
            new BigDecimal("2.1"),
            System.currentTimeMillis()
        ));

        assertFalse(positionState.hasPendingSellOrder());
    }
}
