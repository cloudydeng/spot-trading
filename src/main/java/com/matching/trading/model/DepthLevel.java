package com.matching.trading.model;

import java.math.BigDecimal;

public record DepthLevel(BigDecimal price, BigDecimal quantity) {
}
