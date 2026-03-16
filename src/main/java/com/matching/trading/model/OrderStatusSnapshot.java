package com.matching.trading.model;

import java.math.BigDecimal;

public record OrderStatusSnapshot(
    long orderId,
    String symbol,
    String side,
    String orderType,
    String status,
    String executionType,
    BigDecimal originalQuantity,
    BigDecimal cumulativeFilledQuantity,
    BigDecimal cumulativeQuoteQuantity,
    BigDecimal lastFilledQuantity,
    BigDecimal lastFilledPrice,
    long eventTime
) {
}
