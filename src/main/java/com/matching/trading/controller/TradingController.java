package com.matching.trading.controller;

import com.matching.trading.config.BinanceProperties;
import com.matching.trading.config.TradingProperties;
import com.matching.trading.model.OrderBookView;
import com.matching.trading.model.OrderStatusSnapshot;
import com.matching.trading.model.PositionSnapshot;
import com.matching.trading.model.SignalSnapshot;
import com.matching.trading.model.ExecutionReportSnapshot;
import com.matching.trading.orderbook.LocalOrderBook;
import com.matching.trading.service.OrderBookSyncService;
import com.matching.trading.service.PositionState;
import com.matching.trading.service.SignalEngine;
import com.matching.trading.service.UserDataStreamService;
import com.matching.trading.service.ExecutionReportState;
import com.matching.trading.service.OrderStatusState;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class TradingController {
    private final BinanceProperties binanceProperties;
    private final LocalOrderBook localOrderBook;
    private final SignalEngine signalEngine;
    private final OrderBookSyncService orderBookSyncService;
    private final PositionState positionState;
    private final TradingProperties tradingProperties;
    private final UserDataStreamService userDataStreamService;
    private final ExecutionReportState executionReportState;
    private final OrderStatusState orderStatusState;

    public TradingController(
        BinanceProperties binanceProperties,
        LocalOrderBook localOrderBook,
        SignalEngine signalEngine,
        OrderBookSyncService orderBookSyncService,
        PositionState positionState,
        TradingProperties tradingProperties,
        UserDataStreamService userDataStreamService,
        ExecutionReportState executionReportState,
        OrderStatusState orderStatusState
    ) {
        this.binanceProperties = binanceProperties;
        this.localOrderBook = localOrderBook;
        this.signalEngine = signalEngine;
        this.orderBookSyncService = orderBookSyncService;
        this.positionState = positionState;
        this.tradingProperties = tradingProperties;
        this.userDataStreamService = userDataStreamService;
        this.executionReportState = executionReportState;
        this.orderStatusState = orderStatusState;
    }

    @GetMapping("/")
    public Map<String, Object> home() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "spot-trading");
        response.put("symbol", binanceProperties.getSymbol());
        response.put("endpoints", new String[]{"/health", "/debug/orderbook", "/debug/signal"});
        return response;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", orderBookSyncService.isSynced() ? "UP" : "DEGRADED");
        response.put("symbol", binanceProperties.getSymbol());
        response.put("orderBookSynced", orderBookSyncService.isSynced());
        response.put("lastUpdateId", localOrderBook.getLastUpdateId());
        response.put("lastEventTime", localOrderBook.getLastEventTime());
        response.put("positionOpen", positionState.isOpen());
        response.put("mode", tradingProperties.getStartupMode().name());
        response.put("userDataStreamConnected", userDataStreamService.isConnected());
        return response;
    }

    @GetMapping("/debug/orderbook")
    public OrderBookView orderBook() {
        return localOrderBook.snapshot(10);
    }

    @GetMapping("/debug/signal")
    public SignalSnapshot signal() {
        return signalEngine.snapshot();
    }

    @GetMapping("/debug/position")
    public PositionSnapshot position() {
        return positionState.snapshot(signalEngine.snapshot().lastTradePrice());
    }

    @GetMapping("/debug/execution-report")
    public ExecutionReportSnapshot executionReport() {
        return userDataStreamService.lastExecutionReport();
    }

    @GetMapping("/debug/execution-reports")
    public List<ExecutionReportSnapshot> executionReports() {
        return executionReportState.latest(20);
    }

    @GetMapping("/debug/orders")
    public List<OrderStatusSnapshot> orders() {
        return orderStatusState.latest(20);
    }
}
