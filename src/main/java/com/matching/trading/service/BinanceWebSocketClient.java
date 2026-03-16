package com.matching.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matching.trading.config.BinanceProperties;
import com.matching.trading.model.DepthLevel;
import com.matching.trading.model.DepthUpdateEvent;
import com.matching.trading.model.TradeTick;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BinanceWebSocketClient {
    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketClient.class);

    private final BinanceProperties properties;
    private final ObjectMapper objectMapper;
    private final TradeSignalState tradeSignalState;
    private volatile WebSocket webSocket;
    private Consumer<DepthUpdateEvent> depthConsumer;
    private final AtomicLong lastMessageTime = new AtomicLong(0L);
    private final AtomicLong reconnectAttempts = new AtomicLong(0L);

    public BinanceWebSocketClient(
        BinanceProperties properties,
        ObjectMapper objectMapper,
        TradeSignalState tradeSignalState
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.tradeSignalState = tradeSignalState;
    }

    public synchronized void connect(Consumer<DepthUpdateEvent> depthConsumer) {
        if (webSocket != null) {
            return;
        }
        this.depthConsumer = depthConsumer;
        String symbol = properties.getSymbol().toLowerCase();
        String uri = properties.getWsUrl()
            + "/stream?streams="
            + symbol + "@" + properties.getMarket().getDepthStream()
            + "/" + symbol + "@" + properties.getMarket().getTradeStream();

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getMarket().getRestTimeoutMs()))
            .build();

        webSocket = client.newWebSocketBuilder()
            .buildAsync(URI.create(uri), new Listener())
            .join();
        reconnectAttempts.set(0L);
        log.info("Connected Binance market stream {}", uri);
    }

    public synchronized void reconnect() {
        close();
        applyReconnectBackoff();
        connect(depthConsumer);
    }

    public synchronized void close() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
            webSocket = null;
        }
    }

    public boolean isConnected() {
        return webSocket != null;
    }

    public long getLastMessageTime() {
        return lastMessageTime.get();
    }

    private void applyReconnectBackoff() {
        long attempts = reconnectAttempts.incrementAndGet();
        long baseDelay = properties.getMarket().getWsReconnectDelayMs();
        long maxDelay = properties.getMarket().getMaxReconnectDelayMs();
        long multiplier = 1L << Math.min(attempts - 1L, 5L);
        long delay = Math.min(maxDelay, baseDelay * multiplier);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
            log.info("Binance websocket opened");
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                lastMessageTime.set(System.currentTimeMillis());
                String payload = buffer.toString();
                buffer.setLength(0);
                handleMessage(payload);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.warn("Binance websocket closed: {} {}", statusCode, reason);
            BinanceWebSocketClient.this.webSocket = null;
            lastMessageTime.set(0L);
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("Binance websocket error", error);
            BinanceWebSocketClient.this.webSocket = null;
            lastMessageTime.set(0L);
        }

        private void handleMessage(String payload) {
            try {
                JsonNode root = objectMapper.readTree(payload);
                JsonNode data = root.path("data");
                String stream = root.path("stream").asText("");
                if (stream.contains("@depth")) {
                    depthConsumer.accept(new DepthUpdateEvent(
                        data.path("U").asLong(),
                        data.path("u").asLong(),
                        data.path("E").asLong(),
                        parseLevels(data.path("b")),
                        parseLevels(data.path("a"))
                    ));
                } else if (stream.contains("@trade")) {
                    tradeSignalState.onTrade(new TradeTick(
                        data.path("E").asLong(),
                        new BigDecimal(data.path("p").asText("0")),
                        new BigDecimal(data.path("q").asText("0")),
                        data.path("m").asBoolean()
                    ));
                }
            } catch (Exception e) {
                log.error("Failed to parse websocket payload: {}", payload, e);
            }
        }

        private List<DepthLevel> parseLevels(JsonNode levelsNode) {
            List<DepthLevel> levels = new ArrayList<>();
            for (JsonNode node : levelsNode) {
                levels.add(new DepthLevel(
                    new BigDecimal(node.path(0).asText("0")),
                    new BigDecimal(node.path(1).asText("0"))
                ));
            }
            return levels;
        }
    }
}
