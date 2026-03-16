package com.matching.trading.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matching.trading.model.ExecutionReportSnapshot;
import com.matching.trading.model.MarketOrderResult;
import com.matching.trading.model.SignalSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TradingAuditLogService {
    private static final Logger signalLog = LoggerFactory.getLogger("trading.signal");
    private static final Logger orderLog = LoggerFactory.getLogger("trading.order");
    private static final Logger fillLog = LoggerFactory.getLogger("trading.fill");

    private final ObjectMapper objectMapper;

    public TradingAuditLogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void signal(String event, SignalSnapshot snapshot) {
        signalLog.info("{} {}", event, toJson(snapshot));
    }

    public void order(String event, MarketOrderResult result) {
        orderLog.info("{} {}", event, toJson(result));
    }

    public void executionReport(ExecutionReportSnapshot report) {
        fillLog.info("execution_report {}", toJson(report));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
