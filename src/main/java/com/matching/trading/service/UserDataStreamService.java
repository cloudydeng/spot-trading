package com.matching.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matching.trading.config.BinanceProperties;
import com.matching.trading.config.StrategyProperties;
import com.matching.trading.config.TradingProperties;
import com.matching.trading.model.ExecutionReportSnapshot;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class UserDataStreamService {
    private static final Logger log = LoggerFactory.getLogger(UserDataStreamService.class);
    private static final long KEEPALIVE_STALE_MS = 90_000L;

    private final BinanceRestClient restClient;
    private final BinanceProperties binanceProperties;
    private final StrategyProperties strategyProperties;
    private final TradingProperties tradingProperties;
    private final ObjectMapper objectMapper;
    private final ExecutionReportState executionReportState;
    private final OrderStatusState orderStatusState;
    private final PositionState positionState;
    private final TradingAuditLogService tradingAuditLogService;
    private final TradingRiskState tradingRiskState;

    private volatile WebSocket webSocket;
    private volatile String listenKey;
    private volatile long lastKeepaliveTime;
    private volatile boolean shuttingDown;

    public UserDataStreamService(
        BinanceRestClient restClient,
        BinanceProperties binanceProperties,
        StrategyProperties strategyProperties,
        TradingProperties tradingProperties,
        ObjectMapper objectMapper,
        ExecutionReportState executionReportState,
        OrderStatusState orderStatusState,
        PositionState positionState,
        TradingAuditLogService tradingAuditLogService,
        TradingRiskState tradingRiskState
    ) {
        this.restClient = restClient;
        this.binanceProperties = binanceProperties;
        this.strategyProperties = strategyProperties;
        this.tradingProperties = tradingProperties;
        this.objectMapper = objectMapper;
        this.executionReportState = executionReportState;
        this.orderStatusState = orderStatusState;
        this.positionState = positionState;
        this.tradingAuditLogService = tradingAuditLogService;
        this.tradingRiskState = tradingRiskState;
    }

    public synchronized void startIfNeeded() {
        if (runtimeMode() != TradingProperties.StartupMode.LIVE || shuttingDown) {
            return;
        }
        if (webSocket != null) {
            return;
        }
        try {
            if (listenKey != null) {
                closeListenKey(listenKey);
            }
            listenKey = restClient.createUserDataListenKey();
            webSocket = openWebSocket(listenKey);
            lastKeepaliveTime = System.currentTimeMillis();
            log.info("Started Binance user data stream");
        } catch (Exception e) {
            log.warn("Failed to start Binance user data stream", e);
            webSocket = null;
        }
    }

    public boolean isConnected() {
        return webSocket != null;
    }

    public ExecutionReportSnapshot lastExecutionReport() {
        return executionReportState.snapshot();
    }

    @Scheduled(fixedDelay = 30_000L)
    public void keepAlive() {
        if (runtimeMode() != TradingProperties.StartupMode.LIVE || listenKey == null) {
            return;
        }
        try {
            restClient.keepAliveUserDataListenKey(listenKey);
            lastKeepaliveTime = System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("Failed to keep alive Binance user data stream", e);
            restartStream("keepalive failure");
        }
    }

    @Scheduled(fixedDelay = 5_000L)
    public void monitorConnection() {
        if (runtimeMode() != TradingProperties.StartupMode.LIVE || shuttingDown) {
            return;
        }
        boolean disconnected = webSocket == null;
        boolean staleKeepalive = listenKey != null
            && lastKeepaliveTime > 0
            && System.currentTimeMillis() - lastKeepaliveTime > KEEPALIVE_STALE_MS;
        if (disconnected || staleKeepalive) {
            restartStream(disconnected ? "websocket disconnected" : "keepalive stale");
        }
    }

    @PreDestroy
    public synchronized void stop() {
        shuttingDown = true;
        closeCurrentStream(true);
    }

    private TradingProperties.StartupMode runtimeMode() {
        if (strategyProperties.isLiveTrading()) {
            return TradingProperties.StartupMode.LIVE;
        }
        return tradingProperties.getStartupMode();
    }

    WebSocket openWebSocket(String listenKey) {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(binanceProperties.getMarket().getRestTimeoutMs()))
            .build();
        return client.newWebSocketBuilder()
            .buildAsync(URI.create(binanceProperties.getWsUrl() + "/ws/" + listenKey), new Listener())
            .join();
    }

    private synchronized void restartStream(String reason) {
        if (runtimeMode() != TradingProperties.StartupMode.LIVE || shuttingDown) {
            return;
        }
        log.warn("Restarting Binance user data stream due to {}", reason);
        closeCurrentStream(true);
        startIfNeeded();
    }

    private synchronized void closeCurrentStream(boolean closeListenKey) {
        WebSocket current = webSocket;
        webSocket = null;
        if (current != null) {
            try {
                current.abort();
            } catch (Exception e) {
                log.debug("Failed to abort Binance user data websocket", e);
            }
        }
        if (closeListenKey && listenKey != null) {
            closeListenKey(listenKey);
        }
        if (closeListenKey) {
            listenKey = null;
        }
        lastKeepaliveTime = 0L;
    }

    private void closeListenKey(String currentListenKey) {
        try {
            restClient.closeUserDataListenKey(currentListenKey);
        } catch (Exception e) {
            log.debug("Failed to close Binance listen key", e);
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                handleMessage(buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (UserDataStreamService.this.webSocket == webSocket) {
                UserDataStreamService.this.webSocket = null;
            }
            log.warn("User data stream closed: {} {}", statusCode, reason);
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (UserDataStreamService.this.webSocket == webSocket) {
                UserDataStreamService.this.webSocket = null;
            }
            log.warn("User data stream error", error);
        }

        private void handleMessage(String payload) {
            try {
                JsonNode root = objectMapper.readTree(payload);
                if (!"executionReport".equals(root.path("e").asText())) {
                    return;
                }
                ExecutionReportSnapshot report = new ExecutionReportSnapshot(
                    root.path("s").asText(),
                    root.path("i").asLong(),
                    root.path("S").asText(),
                    root.path("o").asText(),
                    root.path("x").asText(),
                    root.path("X").asText(),
                    new BigDecimal(root.path("q").asText("0")),
                    new BigDecimal(root.path("z").asText("0")),
                    new BigDecimal(root.path("Z").asText("0")),
                    new BigDecimal(root.path("l").asText("0")),
                    new BigDecimal(root.path("L").asText("0")),
                    root.path("E").asLong()
                );
                executionReportState.update(report);
                orderStatusState.update(report);
                PositionState.ClosedPosition closedPosition = positionState.applyExecutionReport(report);
                if (closedPosition != null) {
                    tradingRiskState.recordClosedPosition(
                        closedPosition.entryNotional(),
                        closedPosition.exitNotional(),
                        strategyProperties.getRisk(),
                        Instant.ofEpochMilli(report.eventTime())
                    );
                }
                tradingAuditLogService.executionReport(report);
            } catch (Exception e) {
                log.warn("Failed to parse executionReport payload: {}", payload, e);
            }
        }
    }
}
