package com.matching.trading.model;

public record ApiErrorResponse(int httpStatus, int code, String message, boolean retryable) {
}
