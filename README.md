# spot-trading

`spot-trading` is a single-host Spring Boot service that trades a Binance spot symbol from a locally maintained order book.

It consumes:
- Binance REST snapshots
- Binance market-data websocket streams
- Binance user-data execution reports in live mode

It exposes debug and health endpoints so you can inspect the local book, signal state, position state, and recent order/execution history.

## What the strategy does

The current strategy is a short-horizon buy-pressure breakout model.

It opens a position only when all of these are true:
- order-book bid/ask imbalance is above a configured threshold
- aggressive buy flow is stronger than aggressive sell flow
- spread is below a configured maximum
- price is breaking above the recent breakout reference
- the signal persists long enough and moves far enough in ticks

It exits a position on the first matching condition:
- take profit
- stop loss
- max holding time

Additional runtime protections now include:
- duplicate live sell prevention while an exit order is still pending
- automatic user-data websocket recovery
- max entry orders per minute
- max entry quote amount clamp
- pause after consecutive losing trades

## Runtime modes

The service supports three modes:

- `DRY_RUN`: compute signals only, do not open positions
- `PAPER`: simulate fills locally
- `LIVE`: submit real Binance orders

Important:
- `STRATEGY_LIVE_TRADING=true` forces live execution
- first rollout should use `PAPER`

## Project layout

- [src/main/java/com/matching/trading/TradingApplication.java](/Users/cloudy/IdeaProjects/spot-trading/src/main/java/com/matching/trading/TradingApplication.java): Spring Boot entrypoint
- [src/main/java/com/matching/trading/service/OrderBookSyncService.java](/Users/cloudy/IdeaProjects/spot-trading/src/main/java/com/matching/trading/service/OrderBookSyncService.java): snapshot + delta sync
- [src/main/java/com/matching/trading/service/SignalEngine.java](/Users/cloudy/IdeaProjects/spot-trading/src/main/java/com/matching/trading/service/SignalEngine.java): signal generation
- [src/main/java/com/matching/trading/service/TradeEngine.java](/Users/cloudy/IdeaProjects/spot-trading/src/main/java/com/matching/trading/service/TradeEngine.java): order evaluation and execution
- [src/main/java/com/matching/trading/service/UserDataStreamService.java](/Users/cloudy/IdeaProjects/spot-trading/src/main/java/com/matching/trading/service/UserDataStreamService.java): live execution-report stream management
- [src/main/java/com/matching/trading/controller/TradingController.java](/Users/cloudy/IdeaProjects/spot-trading/src/main/java/com/matching/trading/controller/TradingController.java): HTTP debug endpoints

## Local development

Requirements:
- Java 21
- Maven 3.9+

Run tests:

```bash
mvn -q test
```

Run locally:

```bash
mvn -q spring-boot:run
```

Run in paper mode with environment variables:

```bash
export BINANCE_API_KEY=replace_me
export BINANCE_API_SECRET=replace_me
export BINANCE_SYMBOL=NIGHTUSDT
export TRADING_STARTUP_MODE=PAPER
export STRATEGY_LIVE_TRADING=false
mvn -q spring-boot:run
```

## Key configuration

All main configuration lives in [src/main/resources/application.yml](/Users/cloudy/IdeaProjects/spot-trading/src/main/resources/application.yml).

Important variables:

```bash
SERVER_PORT=8083
BINANCE_API_KEY=
BINANCE_API_SECRET=
BINANCE_SYMBOL=NIGHTUSDT

TRADING_STARTUP_MODE=DRY_RUN
STRATEGY_LIVE_TRADING=false

STRATEGY_QUOTE_AMOUNT=20
STRATEGY_MAX_POSITION_USDT=100
STRATEGY_MAX_ORDERS_PER_MINUTE=6

STRATEGY_IMBALANCE_THRESHOLD=1.6
STRATEGY_AGGRESSIVE_BUY_RATIO_THRESHOLD=1.7
STRATEGY_BREAKOUT_WINDOW_SECONDS=15
STRATEGY_MIN_PRICE_MOVE_TICKS=2
STRATEGY_MIN_SIGNAL_HOLD_MS=2000

STRATEGY_TAKE_PROFIT_RATE=0.008
STRATEGY_STOP_LOSS_RATE=0.005
STRATEGY_MAX_HOLDING_SECONDS=60
STRATEGY_MAX_CONSECUTIVE_LOSSES=3
STRATEGY_PAUSE_MINUTES_AFTER_LOSS_LIMIT=30
```

## HTTP endpoints

Once running, the service exposes:

- `GET /`
- `GET /health`
- `GET /debug/orderbook`
- `GET /debug/signal`
- `GET /debug/position`
- `GET /debug/execution-report`
- `GET /debug/execution-reports`
- `GET /debug/orders`

Example:

```bash
curl http://127.0.0.1:8083/health
curl http://127.0.0.1:8083/debug/orderbook
curl http://127.0.0.1:8083/debug/signal
```

## Logs

Logging is configured in [src/main/resources/logback-spring.xml](/Users/cloudy/IdeaProjects/spot-trading/src/main/resources/logback-spring.xml).

Generated log files:
- `app.log`
- `signals.log`
- `orders.log`
- `fills.log`

## Deployment

For EC2 single-host deployment, see:
- [deploy/AWS_SINGLE_HOST.md](/Users/cloudy/IdeaProjects/spot-trading/deploy/AWS_SINGLE_HOST.md)
- [deploy/spot-trading.service](/Users/cloudy/IdeaProjects/spot-trading/deploy/spot-trading.service)
- [deploy/app.env.example](/Users/cloudy/IdeaProjects/spot-trading/deploy/app.env.example)

## Safety notes

- Do not switch directly to `LIVE` before validating `PAPER`
- This service keeps runtime position state in memory
- Binance credentials must be supplied via environment variables or a secured runtime config source
- `cancel-open-orders-on-startup` and `cancel-open-orders-on-shutdown` are configured but not yet implemented against the Binance API
