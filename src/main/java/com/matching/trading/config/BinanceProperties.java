package com.matching.trading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "binance")
public class BinanceProperties {
    private String apiKey;
    private String apiSecret;
    private String baseUrl;
    private String wsUrl;
    private String symbol;
    private final Market market = new Market();

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getWsUrl() {
        return wsUrl;
    }

    public void setWsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Market getMarket() {
        return market;
    }

    public static class Market {
        private String depthStream;
        private String tradeStream;
        private int snapshotLimit;
        private long recvWindowMs;
        private int restTimeoutMs;
        private long wsReconnectDelayMs;
        private long staleTimeoutMs;
        private long maxReconnectDelayMs;

        public String getDepthStream() {
            return depthStream;
        }

        public void setDepthStream(String depthStream) {
            this.depthStream = depthStream;
        }

        public String getTradeStream() {
            return tradeStream;
        }

        public void setTradeStream(String tradeStream) {
            this.tradeStream = tradeStream;
        }

        public int getSnapshotLimit() {
            return snapshotLimit;
        }

        public void setSnapshotLimit(int snapshotLimit) {
            this.snapshotLimit = snapshotLimit;
        }

        public long getRecvWindowMs() {
            return recvWindowMs;
        }

        public void setRecvWindowMs(long recvWindowMs) {
            this.recvWindowMs = recvWindowMs;
        }

        public int getRestTimeoutMs() {
            return restTimeoutMs;
        }

        public void setRestTimeoutMs(int restTimeoutMs) {
            this.restTimeoutMs = restTimeoutMs;
        }

        public long getWsReconnectDelayMs() {
            return wsReconnectDelayMs;
        }

        public void setWsReconnectDelayMs(long wsReconnectDelayMs) {
            this.wsReconnectDelayMs = wsReconnectDelayMs;
        }

        public long getStaleTimeoutMs() {
            return staleTimeoutMs;
        }

        public void setStaleTimeoutMs(long staleTimeoutMs) {
            this.staleTimeoutMs = staleTimeoutMs;
        }

        public long getMaxReconnectDelayMs() {
            return maxReconnectDelayMs;
        }

        public void setMaxReconnectDelayMs(long maxReconnectDelayMs) {
            this.maxReconnectDelayMs = maxReconnectDelayMs;
        }
    }
}
