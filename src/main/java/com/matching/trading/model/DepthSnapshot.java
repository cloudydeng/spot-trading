package com.matching.trading.model;

import java.util.List;

public record DepthSnapshot(long lastUpdateId, List<DepthLevel> bids, List<DepthLevel> asks) {
}
