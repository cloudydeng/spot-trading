package com.matching.trading.model;

import java.math.BigDecimal;

public record MarketOrderResult(
    String symbol,
    String side,
    String status,
    long orderId,
    BigDecimal executedQty,
    BigDecimal cumulativeQuoteQty,
    BigDecimal averagePrice,
    String rawResponse
) {
}
