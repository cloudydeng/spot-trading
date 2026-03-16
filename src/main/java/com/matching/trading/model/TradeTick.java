package com.matching.trading.model;

import java.math.BigDecimal;

public record TradeTick(long eventTime, BigDecimal price, BigDecimal quantity, boolean buyerIsMaker) {
    public BigDecimal notional() {
        return price.multiply(quantity);
    }
}
