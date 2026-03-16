package com.matching.trading.service;

import com.matching.trading.model.ExchangeFilters;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class ExchangeRuleService {
    private static final MathContext MC = MathContext.DECIMAL64;

    public BigDecimal normalizeQuantity(BigDecimal quantity, ExchangeFilters filters) {
        if (quantity == null || filters == null || filters.stepSize() == null
            || filters.stepSize().compareTo(BigDecimal.ZERO) <= 0) {
            return quantity;
        }
        BigDecimal steps = quantity.divide(filters.stepSize(), 0, RoundingMode.DOWN);
        return steps.multiply(filters.stepSize()).stripTrailingZeros();
    }

    public boolean isSellQuantityValid(BigDecimal quantity, BigDecimal price, ExchangeFilters filters) {
        if (quantity == null || price == null || filters == null) {
            return false;
        }
        if (filters.minQty() != null && filters.minQty().compareTo(BigDecimal.ZERO) > 0
            && quantity.compareTo(filters.minQty()) < 0) {
            return false;
        }
        if (filters.minNotional() != null && filters.minNotional().compareTo(BigDecimal.ZERO) > 0) {
            return quantity.multiply(price).compareTo(filters.minNotional()) >= 0;
        }
        return true;
    }

    public BigDecimal estimateBaseQuantityFromQuote(BigDecimal quoteAmount, BigDecimal price) {
        if (quoteAmount == null || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return quoteAmount.divide(price, MC);
    }
}
