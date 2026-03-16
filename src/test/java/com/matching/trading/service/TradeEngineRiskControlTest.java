package com.matching.trading.service;

import com.matching.trading.config.BinanceProperties;
import com.matching.trading.config.StrategyProperties;
import com.matching.trading.config.TradingProperties;
import com.matching.trading.model.ExchangeFilters;
import com.matching.trading.model.MarketOrderResult;
import com.matching.trading.model.SignalSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TradeEngineRiskControlTest {
    @Test
    void shouldNotPlaceSellWhileAnotherLiveSellOrderIsPending() {
        SignalEngine signalEngine = mock(SignalEngine.class);
        BinanceRestClient restClient = mock(BinanceRestClient.class);
        BinanceProperties binanceProperties = mock(BinanceProperties.class);
        PaperTradingService paperTradingService = mock(PaperTradingService.class);
        UserDataStreamService userDataStreamService = mock(UserDataStreamService.class);
        TradingAuditLogService tradingAuditLogService = mock(TradingAuditLogService.class);
        PositionState positionState = new PositionState();
        ExchangeRuleService exchangeRuleService = new ExchangeRuleService();
        TradingRiskState tradingRiskState = new TradingRiskState();

        StrategyProperties strategyProperties = strategyProperties();
        TradingProperties tradingProperties = new TradingProperties();
        tradingProperties.setStartupMode(TradingProperties.StartupMode.LIVE);

        ExchangeFilters filters = new ExchangeFilters(
            new BigDecimal("0.01"),
            new BigDecimal("0.01"),
            new BigDecimal("0.10"),
            new BigDecimal("5")
        );
        when(restClient.fetchExchangeFilters()).thenReturn(filters);
        when(signalEngine.snapshot()).thenReturn(new SignalSnapshot(
            true,
            true,
            new BigDecimal("2.0"),
            new BigDecimal("2.0"),
            new BigDecimal("5"),
            new BigDecimal("100.00"),
            new BigDecimal("101.50"),
            "buy pressure confirmed"
        ));

        positionState.open(new BigDecimal("1.00"), new BigDecimal("100.00"), new BigDecimal("100.00"), Instant.now());
        positionState.registerSellOrder(99L);

        TradeEngine tradeEngine = new TradeEngine(
            signalEngine,
            restClient,
            binanceProperties,
            strategyProperties,
            tradingProperties,
            positionState,
            paperTradingService,
            exchangeRuleService,
            userDataStreamService,
            tradingAuditLogService,
            tradingRiskState
        );

        assertDoesNotThrow(tradeEngine::init);
        tradeEngine.evaluate();

        verify(restClient, never()).placeMarketSell(any());
    }

    @Test
    void shouldRequireMinimumBreakoutMoveBeforeBuying() {
        SignalEngine signalEngine = mock(SignalEngine.class);
        BinanceRestClient restClient = mock(BinanceRestClient.class);
        BinanceProperties binanceProperties = mock(BinanceProperties.class);
        PaperTradingService paperTradingService = mock(PaperTradingService.class);
        UserDataStreamService userDataStreamService = mock(UserDataStreamService.class);
        TradingAuditLogService tradingAuditLogService = mock(TradingAuditLogService.class);
        PositionState positionState = new PositionState();
        ExchangeRuleService exchangeRuleService = new ExchangeRuleService();
        TradingRiskState tradingRiskState = new TradingRiskState();

        StrategyProperties strategyProperties = strategyProperties();
        strategyProperties.getSignal().setMinPriceMoveTicks(2);
        TradingProperties tradingProperties = new TradingProperties();
        tradingProperties.setStartupMode(TradingProperties.StartupMode.PAPER);

        ExchangeFilters filters = new ExchangeFilters(
            new BigDecimal("0.01"),
            new BigDecimal("0.01"),
            new BigDecimal("0.10"),
            new BigDecimal("5")
        );
        when(restClient.fetchExchangeFilters()).thenReturn(filters);
        when(signalEngine.snapshot()).thenReturn(new SignalSnapshot(
            true,
            true,
            new BigDecimal("2.0"),
            new BigDecimal("2.0"),
            new BigDecimal("5"),
            new BigDecimal("100.00"),
            new BigDecimal("100.01"),
            "buy pressure confirmed"
        ));

        TradeEngine tradeEngine = new TradeEngine(
            signalEngine,
            restClient,
            binanceProperties,
            strategyProperties,
            tradingProperties,
            positionState,
            paperTradingService,
            exchangeRuleService,
            userDataStreamService,
            tradingAuditLogService,
            tradingRiskState
        );

        assertDoesNotThrow(tradeEngine::init);
        tradeEngine.evaluate();

        verifyNoInteractions(paperTradingService);
    }

    @Test
    void shouldRequireSignalToPersistBeforeBuying() throws InterruptedException {
        SignalEngine signalEngine = mock(SignalEngine.class);
        BinanceRestClient restClient = mock(BinanceRestClient.class);
        BinanceProperties binanceProperties = mock(BinanceProperties.class);
        PaperTradingService paperTradingService = mock(PaperTradingService.class);
        UserDataStreamService userDataStreamService = mock(UserDataStreamService.class);
        TradingAuditLogService tradingAuditLogService = mock(TradingAuditLogService.class);
        PositionState positionState = new PositionState();
        ExchangeRuleService exchangeRuleService = new ExchangeRuleService();
        TradingRiskState tradingRiskState = new TradingRiskState();

        StrategyProperties strategyProperties = strategyProperties();
        strategyProperties.getSignal().setMinPriceMoveTicks(0);
        strategyProperties.getSignal().setMinSignalHoldMs(20L);
        TradingProperties tradingProperties = new TradingProperties();
        tradingProperties.setStartupMode(TradingProperties.StartupMode.PAPER);

        ExchangeFilters filters = new ExchangeFilters(
            new BigDecimal("0.01"),
            new BigDecimal("0.01"),
            new BigDecimal("0.10"),
            new BigDecimal("5")
        );
        when(restClient.fetchExchangeFilters()).thenReturn(filters);
        when(binanceProperties.getSymbol()).thenReturn("NIGHTUSDT");
        when(signalEngine.snapshot()).thenReturn(new SignalSnapshot(
            true,
            true,
            new BigDecimal("2.0"),
            new BigDecimal("2.0"),
            new BigDecimal("5"),
            new BigDecimal("100.00"),
            new BigDecimal("100.05"),
            "buy pressure confirmed"
        ));
        when(paperTradingService.simulateBuy(eq("NIGHTUSDT"), eq(new BigDecimal("20")), eq(new BigDecimal("100.05"))))
            .thenReturn(new MarketOrderResult(
                "NIGHTUSDT",
                "BUY",
                "FILLED",
                1L,
                new BigDecimal("0.2"),
                new BigDecimal("20"),
                new BigDecimal("100"),
                "paper"
            ));

        TradeEngine tradeEngine = new TradeEngine(
            signalEngine,
            restClient,
            binanceProperties,
            strategyProperties,
            tradingProperties,
            positionState,
            paperTradingService,
            exchangeRuleService,
            userDataStreamService,
            tradingAuditLogService,
            tradingRiskState
        );

        assertDoesNotThrow(tradeEngine::init);
        tradeEngine.evaluate();
        verifyNoInteractions(paperTradingService);

        Thread.sleep(30L);
        tradeEngine.evaluate();

        verify(paperTradingService).simulateBuy(eq("NIGHTUSDT"), eq(new BigDecimal("20")), eq(new BigDecimal("100.05")));
    }

    private StrategyProperties strategyProperties() {
        StrategyProperties strategyProperties = new StrategyProperties();
        strategyProperties.setEnabled(true);
        strategyProperties.getExecution().setQuoteAmount(new BigDecimal("20"));
        strategyProperties.getExecution().setMaxPositionUsdt(new BigDecimal("100"));
        strategyProperties.getExecution().setCooldownSeconds(0);
        strategyProperties.getExecution().setMaxOrdersPerMinute(10);
        strategyProperties.getExecution().setMinExitIntervalSeconds(0);
        strategyProperties.getSignal().setTopLevels(5);
        strategyProperties.getSignal().setImbalanceThreshold(new BigDecimal("1.6"));
        strategyProperties.getSignal().setAggressiveBuyRatioThreshold(new BigDecimal("1.7"));
        strategyProperties.getSignal().setBreakoutWindowSeconds(15);
        strategyProperties.getSignal().setMinPriceMoveTicks(0);
        strategyProperties.getSignal().setMaxSpreadBps(new BigDecimal("20"));
        strategyProperties.getSignal().setMinSignalHoldMs(0L);
        strategyProperties.getRisk().setTakeProfitRate(new BigDecimal("0.01"));
        strategyProperties.getRisk().setStopLossRate(new BigDecimal("0.01"));
        strategyProperties.getRisk().setMaxHoldingSeconds(60);
        strategyProperties.getRisk().setMaxConsecutiveLosses(3);
        strategyProperties.getRisk().setPauseMinutesAfterLossLimit(30);
        return strategyProperties;
    }
}
