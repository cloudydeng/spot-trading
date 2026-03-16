package com.matching.trading.service;

import com.matching.trading.model.ExchangeFilters;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeRuleServiceTest {
    private final ExchangeRuleService service = new ExchangeRuleService();

    @Test
    void shouldRoundSellQuantityDownToStepSize() {
        ExchangeFilters filters = new ExchangeFilters(
            new BigDecimal("0.0001"),
            new BigDecimal("0.01"),
            new BigDecimal("0.10"),
            new BigDecimal("5")
        );

        BigDecimal normalized = service.normalizeQuantity(new BigDecimal("1.239"), filters);

        assertEquals(new BigDecimal("1.23"), normalized);
    }

    @Test
    void shouldRejectQuantityBelowExchangeMinimums() {
        ExchangeFilters filters = new ExchangeFilters(
            new BigDecimal("0.0001"),
            new BigDecimal("0.01"),
            new BigDecimal("0.10"),
            new BigDecimal("5")
        );

        assertFalse(service.isSellQuantityValid(new BigDecimal("0.05"), new BigDecimal("10"), filters));
        assertTrue(service.isSellQuantityValid(new BigDecimal("0.50"), new BigDecimal("10"), filters));
    }
}
