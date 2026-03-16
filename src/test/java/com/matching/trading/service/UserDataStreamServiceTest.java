package com.matching.trading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matching.trading.config.BinanceProperties;
import com.matching.trading.config.StrategyProperties;
import com.matching.trading.config.TradingProperties;
import java.net.http.WebSocket;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDataStreamServiceTest {
    @Test
    void shouldRestartDisconnectedUserDataStream() {
        BinanceRestClient restClient = mock(BinanceRestClient.class);
        WebSocket webSocket = mock(WebSocket.class);
        StrategyProperties strategyProperties = new StrategyProperties();
        strategyProperties.setLiveTrading(true);
        TradingProperties tradingProperties = new TradingProperties();
        BinanceProperties binanceProperties = new BinanceProperties();
        ExecutionReportState executionReportState = new ExecutionReportState();
        OrderStatusState orderStatusState = new OrderStatusState();
        PositionState positionState = new PositionState();
        TradingRiskState tradingRiskState = new TradingRiskState();
        TradingAuditLogService tradingAuditLogService = new TradingAuditLogService(new ObjectMapper());

        when(restClient.createUserDataListenKey()).thenReturn("listen-key-1", "listen-key-2");

        TestUserDataStreamService service = new TestUserDataStreamService(
            restClient,
            binanceProperties,
            strategyProperties,
            tradingProperties,
            new ObjectMapper(),
            executionReportState,
            orderStatusState,
            positionState,
            tradingAuditLogService,
            tradingRiskState,
            webSocket
        );

        service.startIfNeeded();
        ReflectionTestUtils.setField(service, "webSocket", null);

        service.monitorConnection();

        verify(restClient).closeUserDataListenKey("listen-key-1");
        verify(restClient, times(2)).createUserDataListenKey();
    }

    private static final class TestUserDataStreamService extends UserDataStreamService {
        private final WebSocket openedWebSocket;

        private TestUserDataStreamService(
            BinanceRestClient restClient,
            BinanceProperties binanceProperties,
            StrategyProperties strategyProperties,
            TradingProperties tradingProperties,
            ObjectMapper objectMapper,
            ExecutionReportState executionReportState,
            OrderStatusState orderStatusState,
            PositionState positionState,
            TradingAuditLogService tradingAuditLogService,
            TradingRiskState tradingRiskState,
            WebSocket webSocket
        ) {
            super(
                restClient,
                binanceProperties,
                strategyProperties,
                tradingProperties,
                objectMapper,
                executionReportState,
                orderStatusState,
                positionState,
                tradingAuditLogService,
                tradingRiskState
            );
            this.openedWebSocket = webSocket;
        }

        @Override
        WebSocket openWebSocket(String listenKey) {
            return openedWebSocket;
        }
    }
}
