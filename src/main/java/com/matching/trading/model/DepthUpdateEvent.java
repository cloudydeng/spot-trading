package com.matching.trading.model;

import java.util.List;

public record DepthUpdateEvent(
    long firstUpdateId,
    long finalUpdateId,
    long eventTime,
    List<DepthLevel> bids,
    List<DepthLevel> asks
) {
}
