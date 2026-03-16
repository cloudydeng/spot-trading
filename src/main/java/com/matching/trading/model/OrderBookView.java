package com.matching.trading.model;

import java.math.BigDecimal;
import java.util.List;

public record OrderBookView(
    long lastUpdateId,
    boolean synced,
    BigDecimal bestBid,
    BigDecimal bestAsk,
    long lastEventTime,
    List<DepthLevel> bids,
    List<DepthLevel> asks
) {
}
