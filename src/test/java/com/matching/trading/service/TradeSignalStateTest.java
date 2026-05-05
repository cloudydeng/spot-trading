package com.matching.trading.service;

import com.matching.trading.config.StrategyProperties;
import com.matching.trading.model.TradeTick;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeSignalStateTest {
    @Test
    void shouldRetainTradesForConfiguredBreakoutWindow() {
        StrategyProperties strategyProperties = new StrategyProperties();
        strategyProperties.getSignal().setBreakoutWindowSeconds(15);
        strategyProperties.getSignal().setMinSignalHoldMs(2_000L);
        TradeSignalState state = new TradeSignalState(strategyProperties);

        long now = System.currentTimeMillis();
        state.onTrade(new TradeTick(now - 12_000L, new BigDecimal("101"), BigDecimal.ONE, false));
        state.onTrade(new TradeTick(now, new BigDecimal("100"), BigDecimal.ONE, false));

        assertEquals(new BigDecimal("101"), state.breakoutReference(15_000L));
    }

    @Test
    void shouldIgnoreCurrentTradeWhenCalculatingBreakoutReference() {
        StrategyProperties strategyProperties = new StrategyProperties();
        strategyProperties.getSignal().setBreakoutWindowSeconds(15);
        strategyProperties.getSignal().setMinSignalHoldMs(2_000L);
        TradeSignalState state = new TradeSignalState(strategyProperties);

        long now = System.currentTimeMillis();
        state.onTrade(new TradeTick(now - 8_000L, new BigDecimal("100"), BigDecimal.ONE, false));
        state.onTrade(new TradeTick(now + 1_000L, new BigDecimal("105"), BigDecimal.ONE, false));

        assertEquals(new BigDecimal("100"), state.breakoutReference(15_000L));
    }
}
