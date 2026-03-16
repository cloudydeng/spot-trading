package com.matching.trading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading")
public class TradingProperties {
    private StartupMode startupMode = StartupMode.DRY_RUN;
    private boolean cancelOpenOrdersOnStartup;
    private boolean cancelOpenOrdersOnShutdown;
    private final Api api = new Api();

    public StartupMode getStartupMode() {
        return startupMode;
    }

    public void setStartupMode(StartupMode startupMode) {
        this.startupMode = startupMode;
    }

    public boolean isCancelOpenOrdersOnStartup() {
        return cancelOpenOrdersOnStartup;
    }

    public void setCancelOpenOrdersOnStartup(boolean cancelOpenOrdersOnStartup) {
        this.cancelOpenOrdersOnStartup = cancelOpenOrdersOnStartup;
    }

    public boolean isCancelOpenOrdersOnShutdown() {
        return cancelOpenOrdersOnShutdown;
    }

    public void setCancelOpenOrdersOnShutdown(boolean cancelOpenOrdersOnShutdown) {
        this.cancelOpenOrdersOnShutdown = cancelOpenOrdersOnShutdown;
    }

    public Api getApi() {
        return api;
    }

    public enum StartupMode {
        DRY_RUN,
        PAPER,
        LIVE
    }

    public static class Api {
        private int retryCooldownSeconds = 30;

        public int getRetryCooldownSeconds() {
            return retryCooldownSeconds;
        }

        public void setRetryCooldownSeconds(int retryCooldownSeconds) {
            this.retryCooldownSeconds = retryCooldownSeconds;
        }
    }
}
