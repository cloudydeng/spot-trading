package com.matching.trading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orderbook")
public class OrderBookProperties {
    private boolean enabled;
    private boolean rebuildOnGap;
    private boolean checkCrossedBook;
    private int logTopLevels;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRebuildOnGap() {
        return rebuildOnGap;
    }

    public void setRebuildOnGap(boolean rebuildOnGap) {
        this.rebuildOnGap = rebuildOnGap;
    }

    public boolean isCheckCrossedBook() {
        return checkCrossedBook;
    }

    public void setCheckCrossedBook(boolean checkCrossedBook) {
        this.checkCrossedBook = checkCrossedBook;
    }

    public int getLogTopLevels() {
        return logTopLevels;
    }

    public void setLogTopLevels(int logTopLevels) {
        this.logTopLevels = logTopLevels;
    }
}
