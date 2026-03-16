package com.matching.trading.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionSnapshot(
    boolean open,
    BigDecimal quantity,
    BigDecimal entryPrice,
    BigDecimal notional,
    Instant openedAt,
    BigDecimal lastPrice,
    BigDecimal unrealizedPnlRate
) {
}
