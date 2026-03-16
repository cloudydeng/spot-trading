package com.matching.trading.service;

import com.matching.trading.config.OrderBookProperties;
import com.matching.trading.model.DepthSnapshot;
import com.matching.trading.model.DepthUpdateEvent;
import com.matching.trading.orderbook.LocalOrderBook;
import jakarta.annotation.PreDestroy;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OrderBookSyncService {
    private static final Logger log = LoggerFactory.getLogger(OrderBookSyncService.class);

    private final BinanceRestClient restClient;
    private final BinanceWebSocketClient webSocketClient;
    private final LocalOrderBook localOrderBook;
    private final OrderBookProperties properties;
    private final Queue<DepthUpdateEvent> pendingEvents = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean bootstrapComplete = new AtomicBoolean(false);

    public OrderBookSyncService(
        BinanceRestClient restClient,
        BinanceWebSocketClient webSocketClient,
        LocalOrderBook localOrderBook,
        OrderBookProperties properties
    ) {
        this.restClient = restClient;
        this.webSocketClient = webSocketClient;
        this.localOrderBook = localOrderBook;
        this.properties = properties;
    }

    public void start() {
        if (!properties.isEnabled()) {
            log.info("Order book sync is disabled");
            return;
        }
        webSocketClient.connect(this::onDepthEvent);
        bootstrap();
    }

    public boolean isSynced() {
        return localOrderBook.isSynced();
    }

    public void rebuild() {
        log.warn("Rebuilding local order book");
        localOrderBook.markUnsynced();
        pendingEvents.clear();
        bootstrapComplete.set(false);
        bootstrap();
    }

    @PreDestroy
    public void stop() {
        webSocketClient.close();
    }

    @Scheduled(fixedDelay = 2000L)
    public void monitorConnection() {
        if (!properties.isEnabled()) {
            return;
        }

        if (!bootstrapComplete.get()) {
            bootstrap();
            return;
        }

        long lastMessageTime = webSocketClient.getLastMessageTime();
        boolean stale = lastMessageTime > 0
            && System.currentTimeMillis() - lastMessageTime > 5_000L;
        if (!webSocketClient.isConnected() || stale) {
            log.warn("Binance market stream unhealthy, reconnecting. connected={}, stale={}",
                webSocketClient.isConnected(), stale);
            localOrderBook.markUnsynced();
            pendingEvents.clear();
            bootstrapComplete.set(false);
            try {
                webSocketClient.reconnect();
                bootstrap();
            } catch (Exception e) {
                log.warn("Reconnect/bootstrap attempt failed", e);
            }
        }
    }

    private synchronized void bootstrap() {
        DepthSnapshot snapshot;
        try {
            snapshot = restClient.fetchDepthSnapshot();
        } catch (Exception e) {
            log.warn("Failed to fetch depth snapshot during bootstrap", e);
            return;
        }
        localOrderBook.loadSnapshot(snapshot);

        while (!pendingEvents.isEmpty()) {
            DepthUpdateEvent event = pendingEvents.poll();
            if (event == null || event.finalUpdateId() <= snapshot.lastUpdateId()) {
                continue;
            }
            if (event.firstUpdateId() > localOrderBook.getLastUpdateId() + 1L && properties.isRebuildOnGap()) {
                rebuild();
                return;
            }
            if (event.firstUpdateId() <= localOrderBook.getLastUpdateId()
                && event.finalUpdateId() >= localOrderBook.getLastUpdateId()) {
                localOrderBook.apply(event);
            } else if (event.firstUpdateId() == localOrderBook.getLastUpdateId() + 1L) {
                localOrderBook.apply(event);
            }
        }
        bootstrapComplete.set(true);
        log.info("Local order book synced at updateId={}", localOrderBook.getLastUpdateId());
    }

    private void onDepthEvent(DepthUpdateEvent event) {
        if (!bootstrapComplete.get()) {
            pendingEvents.add(event);
            return;
        }

        long localUpdateId = localOrderBook.getLastUpdateId();
        if (event.finalUpdateId() <= localUpdateId) {
            return;
        }
        if (event.firstUpdateId() > localUpdateId + 1L) {
            rebuild();
            return;
        }
        localOrderBook.apply(event);
    }
}
