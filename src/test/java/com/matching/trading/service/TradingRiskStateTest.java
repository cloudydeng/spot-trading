package com.matching.trading.service;

import com.matching.trading.config.StrategyProperties;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingRiskStateTest {
    @Test
    void shouldStopAcceptingEntriesWhenRateLimitIsReached() {
        TradingRiskState state = new TradingRiskState();
        Instant now = Instant.now();

        assertTrue(state.canSubmitEntryOrder(now, 2));
        state.recordEntryOrder(now);
        assertTrue(state.canSubmitEntryOrder(now.plusSeconds(1), 2));
        state.recordEntryOrder(now.plusSeconds(1));

        assertFalse(state.canSubmitEntryOrder(now.plusSeconds(2), 2));
    }

    @Test
    void shouldPauseEntriesAfterConfiguredConsecutiveLosses() {
        TradingRiskState state = new TradingRiskState();
        StrategyProperties.Risk risk = new StrategyProperties.Risk();
        risk.setMaxConsecutiveLosses(2);
        risk.setPauseMinutesAfterLossLimit(5);

        Instant now = Instant.now();
        state.recordClosedPosition(new BigDecimal("100"), new BigDecimal("90"), risk, now);
        assertFalse(state.isEntryPaused(now));

        state.recordClosedPosition(new BigDecimal("100"), new BigDecimal("80"), risk, now.plusSeconds(1));

        assertTrue(state.isEntryPaused(now.plusSeconds(2)));
    }
}
