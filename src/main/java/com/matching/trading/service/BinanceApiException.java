package com.matching.trading.service;

import com.matching.trading.model.ApiErrorResponse;

public class BinanceApiException extends RuntimeException {
    private final ApiErrorResponse error;

    public BinanceApiException(ApiErrorResponse error) {
        super("Binance API error: httpStatus=" + error.httpStatus() + ", code=" + error.code() + ", message=" + error.message());
        this.error = error;
    }

    public ApiErrorResponse getError() {
        return error;
    }
}
