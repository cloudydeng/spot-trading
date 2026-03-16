package com.matching.trading.model;

import java.math.BigDecimal;

public record ExchangeFilters(
    BigDecimal tickSize,
    BigDecimal stepSize,
    BigDecimal minQty,
    BigDecimal minNotional
) {
}
