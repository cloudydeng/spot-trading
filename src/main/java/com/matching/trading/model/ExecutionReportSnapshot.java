package com.matching.trading.model;

import java.math.BigDecimal;

public record ExecutionReportSnapshot(
    String symbol,
    long orderId,
    String side,
    String orderType,
    String executionType,
    String orderStatus,
    BigDecimal orderQuantity,
    BigDecimal cumulativeFilledQuantity,
    BigDecimal cumulativeQuoteQuantity,
    BigDecimal lastFilledQuantity,
    BigDecimal lastFilledPrice,
    long eventTime
) {
}
