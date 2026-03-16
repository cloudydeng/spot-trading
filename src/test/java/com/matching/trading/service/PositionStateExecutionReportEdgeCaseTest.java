package com.matching.trading.service;

import com.matching.trading.model.ExecutionReportSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionStateExecutionReportEdgeCaseTest {
    @Test
    void shouldKeepRemainingPositionWhenSellOrderIsCanceledAfterPartialFill() {
        PositionState positionState = new PositionState();
        positionState.open(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("2"), Instant.now());
        positionState.registerSellOrder(3L);

        positionState.applyExecutionReport(new ExecutionReportSnapshot(
            "NIGHTUSDT",
            3L,
            "SELL",
            "MARKET",
            "CANCELED",
            "CANCELED",
            new BigDecimal("10"),
            new BigDecimal("4"),
            new BigDecimal("8"),
            new BigDecimal("4"),
            new BigDecimal("2"),
            System.currentTimeMillis()
        ));

        assertTrue(positionState.isOpen());
        assertEquals(new BigDecimal("6"), positionState.quantity());
    }

    @Test
    void shouldCloseDustPositionAfterFilledSellThatWasRoundedDown() {
        PositionState positionState = new PositionState();
        positionState.open(new BigDecimal("1.239"), new BigDecimal("2.478"), new BigDecimal("2"), Instant.now());
        positionState.registerSellOrder(4L);

        positionState.applyExecutionReport(new ExecutionReportSnapshot(
            "NIGHTUSDT",
            4L,
            "SELL",
            "MARKET",
            "TRADE",
            "FILLED",
            new BigDecimal("1.23"),
            new BigDecimal("1.23"),
            new BigDecimal("2.46"),
            new BigDecimal("1.23"),
            new BigDecimal("2"),
            System.currentTimeMillis()
        ));

        assertFalse(positionState.isOpen());
        assertEquals(BigDecimal.ZERO, positionState.quantity());
    }
}
