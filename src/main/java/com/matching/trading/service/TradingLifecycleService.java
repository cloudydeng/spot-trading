package com.matching.trading.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class TradingLifecycleService implements ApplicationRunner {
    private final OrderBookSyncService orderBookSyncService;
    private final UserDataStreamService userDataStreamService;

    public TradingLifecycleService(OrderBookSyncService orderBookSyncService, UserDataStreamService userDataStreamService) {
        this.orderBookSyncService = orderBookSyncService;
        this.userDataStreamService = userDataStreamService;
    }

    @Override
    public void run(ApplicationArguments args) {
        orderBookSyncService.start();
        userDataStreamService.startIfNeeded();
    }
}
