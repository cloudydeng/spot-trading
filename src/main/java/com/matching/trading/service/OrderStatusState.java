package com.matching.trading.service;

import com.matching.trading.model.ExecutionReportSnapshot;
import com.matching.trading.model.OrderStatusSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusState {
    private final ConcurrentMap<Long, OrderStatusSnapshot> orders = new ConcurrentHashMap<>();

    public void update(ExecutionReportSnapshot report) {
        if (report == null) {
            return;
        }
        orders.put(report.orderId(), new OrderStatusSnapshot(
            report.orderId(),
            report.symbol(),
            report.side(),
            report.orderType(),
            report.orderStatus(),
            report.executionType(),
            report.orderQuantity(),
            report.cumulativeFilledQuantity(),
            report.cumulativeQuoteQuantity(),
            report.lastFilledQuantity(),
            report.lastFilledPrice(),
            report.eventTime()
        ));
    }

    public OrderStatusSnapshot get(long orderId) {
        return orders.get(orderId);
    }

    public List<OrderStatusSnapshot> latest(int limit) {
        return orders.values().stream()
            .sorted(Comparator.comparingLong(OrderStatusSnapshot::eventTime).reversed())
            .limit(limit)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}
