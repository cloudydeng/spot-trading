package com.matching.trading.service;

import com.matching.trading.model.ExecutionReportSnapshot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExecutionReportState {
    private static final int MAX_HISTORY = 50;

    private volatile ExecutionReportSnapshot lastReport;
    private final Deque<ExecutionReportSnapshot> history = new ArrayDeque<>();

    public synchronized void update(ExecutionReportSnapshot report) {
        this.lastReport = report;
        history.addFirst(report);
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    public ExecutionReportSnapshot snapshot() {
        return lastReport;
    }

    public synchronized List<ExecutionReportSnapshot> latest(int limit) {
        List<ExecutionReportSnapshot> reports = new ArrayList<>(Math.min(limit, history.size()));
        int count = 0;
        for (ExecutionReportSnapshot report : history) {
            reports.add(report);
            count++;
            if (count >= limit) {
                break;
            }
        }
        return reports;
    }
}
