package com.matching.trading.model;

import java.math.BigDecimal;

public record SignalSnapshot(
    boolean ready,
    boolean buyPressureDetected,
    BigDecimal imbalanceRatio,
    BigDecimal aggressiveBuyRatio,
    BigDecimal spreadBps,
    BigDecimal breakoutPrice,
    BigDecimal lastTradePrice,
    String reason
) {
}
