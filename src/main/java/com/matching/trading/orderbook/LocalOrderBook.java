package com.matching.trading.orderbook;

import com.matching.trading.model.DepthLevel;
import com.matching.trading.model.DepthSnapshot;
import com.matching.trading.model.DepthUpdateEvent;
import com.matching.trading.model.OrderBookView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Component;

@Component
public class LocalOrderBook {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final NavigableMap<BigDecimal, BigDecimal> bids =
        new ConcurrentSkipListMap<>(Comparator.reverseOrder());
    private final NavigableMap<BigDecimal, BigDecimal> asks =
        new ConcurrentSkipListMap<>();

    private volatile long lastUpdateId;
    private volatile long lastEventTime;
    private volatile boolean synced;

    public void loadSnapshot(DepthSnapshot snapshot) {
        lock.writeLock().lock();
        try {
            bids.clear();
            asks.clear();
            snapshot.bids().forEach(level -> upsertLevel(bids, level));
            snapshot.asks().forEach(level -> upsertLevel(asks, level));
            lastUpdateId = snapshot.lastUpdateId();
            synced = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void apply(DepthUpdateEvent event) {
        lock.writeLock().lock();
        try {
            event.bids().forEach(level -> upsertLevel(bids, level));
            event.asks().forEach(level -> upsertLevel(asks, level));
            lastUpdateId = event.finalUpdateId();
            lastEventTime = event.eventTime();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void markUnsynced() {
        synced = false;
    }

    public long getLastUpdateId() {
        return lastUpdateId;
    }

    public long getLastEventTime() {
        return lastEventTime;
    }

    public boolean isSynced() {
        return synced;
    }

    public OrderBookView snapshot(int levels) {
        lock.readLock().lock();
        try {
            List<DepthLevel> topBids = topLevels(bids, levels);
            List<DepthLevel> topAsks = topLevels(asks, levels);
            BigDecimal bestBid = topBids.isEmpty() ? null : topBids.get(0).price();
            BigDecimal bestAsk = topAsks.isEmpty() ? null : topAsks.get(0).price();
            return new OrderBookView(lastUpdateId, synced, bestBid, bestAsk, lastEventTime, topBids, topAsks);
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<DepthLevel> topLevels(NavigableMap<BigDecimal, BigDecimal> side, int levels) {
        List<DepthLevel> result = new ArrayList<>(levels);
        int count = 0;
        for (var entry : side.entrySet()) {
            result.add(new DepthLevel(entry.getKey(), entry.getValue()));
            count++;
            if (count >= levels) {
                break;
            }
        }
        return result;
    }

    private void upsertLevel(NavigableMap<BigDecimal, BigDecimal> side, DepthLevel level) {
        if (level.quantity().compareTo(BigDecimal.ZERO) == 0) {
            side.remove(level.price());
        } else {
            side.put(level.price(), level.quantity());
        }
    }
}
