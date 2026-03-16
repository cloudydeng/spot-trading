package com.matching.trading.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "strategy")
public class StrategyProperties {
    private boolean enabled;
    private boolean liveTrading;
    private final Execution execution = new Execution();
    private final Signal signal = new Signal();
    private final Risk risk = new Risk();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLiveTrading() {
        return liveTrading;
    }

    public void setLiveTrading(boolean liveTrading) {
        this.liveTrading = liveTrading;
    }

    public Execution getExecution() {
        return execution;
    }

    public Signal getSignal() {
        return signal;
    }

    public Risk getRisk() {
        return risk;
    }

    public static class Execution {
        private String orderType;
        private BigDecimal quoteAmount;
        private BigDecimal maxPositionUsdt;
        private int cooldownSeconds;
        private int maxOrdersPerMinute;
        private int minExitIntervalSeconds;

        public String getOrderType() {
            return orderType;
        }

        public void setOrderType(String orderType) {
            this.orderType = orderType;
        }

        public BigDecimal getQuoteAmount() {
            return quoteAmount;
        }

        public void setQuoteAmount(BigDecimal quoteAmount) {
            this.quoteAmount = quoteAmount;
        }

        public BigDecimal getMaxPositionUsdt() {
            return maxPositionUsdt;
        }

        public void setMaxPositionUsdt(BigDecimal maxPositionUsdt) {
            this.maxPositionUsdt = maxPositionUsdt;
        }

        public int getCooldownSeconds() {
            return cooldownSeconds;
        }

        public void setCooldownSeconds(int cooldownSeconds) {
            this.cooldownSeconds = cooldownSeconds;
        }

        public int getMaxOrdersPerMinute() {
            return maxOrdersPerMinute;
        }

        public void setMaxOrdersPerMinute(int maxOrdersPerMinute) {
            this.maxOrdersPerMinute = maxOrdersPerMinute;
        }

        public int getMinExitIntervalSeconds() {
            return minExitIntervalSeconds;
        }

        public void setMinExitIntervalSeconds(int minExitIntervalSeconds) {
            this.minExitIntervalSeconds = minExitIntervalSeconds;
        }
    }

    public static class Signal {
        private int topLevels;
        private BigDecimal imbalanceThreshold;
        private BigDecimal aggressiveBuyRatioThreshold;
        private int breakoutWindowSeconds;
        private int minPriceMoveTicks;
        private BigDecimal maxSpreadBps;
        private long minSignalHoldMs;

        public int getTopLevels() {
            return topLevels;
        }

        public void setTopLevels(int topLevels) {
            this.topLevels = topLevels;
        }

        public BigDecimal getImbalanceThreshold() {
            return imbalanceThreshold;
        }

        public void setImbalanceThreshold(BigDecimal imbalanceThreshold) {
            this.imbalanceThreshold = imbalanceThreshold;
        }

        public BigDecimal getAggressiveBuyRatioThreshold() {
            return aggressiveBuyRatioThreshold;
        }

        public void setAggressiveBuyRatioThreshold(BigDecimal aggressiveBuyRatioThreshold) {
            this.aggressiveBuyRatioThreshold = aggressiveBuyRatioThreshold;
        }

        public int getBreakoutWindowSeconds() {
            return breakoutWindowSeconds;
        }

        public void setBreakoutWindowSeconds(int breakoutWindowSeconds) {
            this.breakoutWindowSeconds = breakoutWindowSeconds;
        }

        public int getMinPriceMoveTicks() {
            return minPriceMoveTicks;
        }

        public void setMinPriceMoveTicks(int minPriceMoveTicks) {
            this.minPriceMoveTicks = minPriceMoveTicks;
        }

        public BigDecimal getMaxSpreadBps() {
            return maxSpreadBps;
        }

        public void setMaxSpreadBps(BigDecimal maxSpreadBps) {
            this.maxSpreadBps = maxSpreadBps;
        }

        public long getMinSignalHoldMs() {
            return minSignalHoldMs;
        }

        public void setMinSignalHoldMs(long minSignalHoldMs) {
            this.minSignalHoldMs = minSignalHoldMs;
        }
    }

    public static class Risk {
        private BigDecimal takeProfitRate;
        private BigDecimal stopLossRate;
        private int maxHoldingSeconds;
        private int maxConsecutiveLosses;
        private int pauseMinutesAfterLossLimit;

        public BigDecimal getTakeProfitRate() {
            return takeProfitRate;
        }

        public void setTakeProfitRate(BigDecimal takeProfitRate) {
            this.takeProfitRate = takeProfitRate;
        }

        public BigDecimal getStopLossRate() {
            return stopLossRate;
        }

        public void setStopLossRate(BigDecimal stopLossRate) {
            this.stopLossRate = stopLossRate;
        }

        public int getMaxHoldingSeconds() {
            return maxHoldingSeconds;
        }

        public void setMaxHoldingSeconds(int maxHoldingSeconds) {
            this.maxHoldingSeconds = maxHoldingSeconds;
        }

        public int getMaxConsecutiveLosses() {
            return maxConsecutiveLosses;
        }

        public void setMaxConsecutiveLosses(int maxConsecutiveLosses) {
            this.maxConsecutiveLosses = maxConsecutiveLosses;
        }

        public int getPauseMinutesAfterLossLimit() {
            return pauseMinutesAfterLossLimit;
        }

        public void setPauseMinutesAfterLossLimit(int pauseMinutesAfterLossLimit) {
            this.pauseMinutesAfterLossLimit = pauseMinutesAfterLossLimit;
        }
    }
}
