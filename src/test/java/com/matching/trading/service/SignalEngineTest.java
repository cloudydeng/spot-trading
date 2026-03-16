package com.matching.trading.service;

import com.matching.trading.config.StrategyProperties;
import com.matching.trading.model.DepthLevel;
import com.matching.trading.model.OrderBookView;
import com.matching.trading.model.SignalSnapshot;
import com.matching.trading.orderbook.LocalOrderBook;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignalEngineTest {
    @Test
    void shouldTriggerWhenThreeOfFourConditionsMatch() {
        LocalOrderBook localOrderBook = mock(LocalOrderBook.class);
        TradeSignalState tradeSignalState = mock(TradeSignalState.class);
        StrategyProperties strategyProperties = strategyProperties();
        SignalEngine signalEngine = new SignalEngine(localOrderBook, tradeSignalState, strategyProperties);

        when(localOrderBook.snapshot(5)).thenReturn(new OrderBookView(
            1L,
            true,
            new BigDecimal("100.00"),
            new BigDecimal("100.03"),
            1L,
            List.of(
                new DepthLevel(new BigDecimal("100.00"), new BigDecimal("8")),
                new DepthLevel(new BigDecimal("99.99"), new BigDecimal("7"))
            ),
            List.of(
                new DepthLevel(new BigDecimal("100.03"), new BigDecimal("5")),
                new DepthLevel(new BigDecimal("100.04"), new BigDecimal("5"))
            )
        ));
        when(tradeSignalState.aggressiveBuyRatio()).thenReturn(new BigDecimal("1.20"));
        when(tradeSignalState.breakoutReference(15_000L)).thenReturn(new BigDecimal("100.05"));
        when(tradeSignalState.lastTradePrice()).thenReturn(new BigDecimal("100.04"));

        SignalSnapshot snapshot = signalEngine.snapshot();

        assertTrue(snapshot.buyPressureDetected());
    }

    @Test
    void shouldNotTriggerWhenOnlyTwoConditionsMatch() {
        LocalOrderBook localOrderBook = mock(LocalOrderBook.class);
        TradeSignalState tradeSignalState = mock(TradeSignalState.class);
        StrategyProperties strategyProperties = strategyProperties();
        SignalEngine signalEngine = new SignalEngine(localOrderBook, tradeSignalState, strategyProperties);

        when(localOrderBook.snapshot(5)).thenReturn(new OrderBookView(
            1L,
            true,
            new BigDecimal("100.00"),
            new BigDecimal("100.10"),
            1L,
            List.of(
                new DepthLevel(new BigDecimal("100.00"), new BigDecimal("6")),
                new DepthLevel(new BigDecimal("99.99"), new BigDecimal("6"))
            ),
            List.of(
                new DepthLevel(new BigDecimal("100.10"), new BigDecimal("5")),
                new DepthLevel(new BigDecimal("100.11"), new BigDecimal("5"))
            )
        ));
        when(tradeSignalState.aggressiveBuyRatio()).thenReturn(new BigDecimal("1.00"));
        when(tradeSignalState.breakoutReference(15_000L)).thenReturn(new BigDecimal("100.20"));
        when(tradeSignalState.lastTradePrice()).thenReturn(new BigDecimal("100.10"));

        SignalSnapshot snapshot = signalEngine.snapshot();

        assertFalse(snapshot.buyPressureDetected());
    }

    private StrategyProperties strategyProperties() {
        StrategyProperties strategyProperties = new StrategyProperties();
        strategyProperties.getSignal().setTopLevels(5);
        strategyProperties.getSignal().setImbalanceThreshold(new BigDecimal("1.20"));
        strategyProperties.getSignal().setAggressiveBuyRatioThreshold(new BigDecimal("1.10"));
        strategyProperties.getSignal().setBreakoutWindowSeconds(15);
        strategyProperties.getSignal().setMaxSpreadBps(new BigDecimal("5"));
        strategyProperties.getSignal().setMinConditionsToTrigger(3);
        return strategyProperties;
    }
}
