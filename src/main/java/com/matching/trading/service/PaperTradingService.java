package com.matching.trading.service;

import com.matching.trading.model.MarketOrderResult;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class PaperTradingService {
    private static final MathContext MC = MathContext.DECIMAL64;

    private final AtomicLong orderIdSequence = new AtomicLong(1_000_000L);

    public MarketOrderResult simulateBuy(String symbol, BigDecimal quoteAmount, BigDecimal marketPrice) {
        BigDecimal price = normalizePrice(marketPrice);
        BigDecimal executedQty = price.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : quoteAmount.divide(price, MC);
        return new MarketOrderResult(
            symbol,
            "BUY",
            "FILLED",
            orderIdSequence.incrementAndGet(),
            executedQty,
            quoteAmount,
            price,
            "PAPER_BUY"
        );
    }

    public MarketOrderResult simulateSell(String symbol, BigDecimal quantity, BigDecimal marketPrice) {
        BigDecimal price = normalizePrice(marketPrice);
        BigDecimal quoteQty = quantity.multiply(price, MC);
        return new MarketOrderResult(
            symbol,
            "SELL",
            "FILLED",
            orderIdSequence.incrementAndGet(),
            quantity,
            quoteQty,
            price,
            "PAPER_SELL"
        );
    }

    private BigDecimal normalizePrice(BigDecimal marketPrice) {
        return marketPrice == null ? BigDecimal.ZERO : marketPrice;
    }
}
