# AWS Single Host Deployment

1. Build the jar locally:
```bash
mvn -q test
mvn -q package -DskipTests
```

2. Copy the jar and deployment files to the EC2 host:
```bash
scp target/*.jar ec2-user@YOUR_HOST:/opt/spot-trading/app.jar
scp deploy/app.env.example ec2-user@YOUR_HOST:/opt/spot-trading/app.env
scp deploy/spot-trading.service ec2-user@YOUR_HOST:/tmp/spot-trading.service
```

3. Install Java 21 on the host, then move the systemd service:
```bash
sudo mv /tmp/spot-trading.service /etc/systemd/system/spot-trading.service
sudo systemctl daemon-reload
sudo systemctl enable spot-trading.service
```

4. Edit `/opt/spot-trading/app.env` with real Binance credentials and keep:
- `TRADING_STARTUP_MODE=PAPER` for first boot
- `STRATEGY_LIVE_TRADING=true` to place real orders
- outbound access to `api.binance.com` and `stream.binance.com:9443`

5. Start and inspect:
```bash
sudo systemctl start spot-trading.service
sudo systemctl status spot-trading.service
journalctl -u spot-trading.service -f
```

6. Validate the service:
```bash
curl http://127.0.0.1:8083/health
curl http://127.0.0.1:8083/debug/orderbook
curl http://127.0.0.1:8083/debug/signal
curl http://127.0.0.1:8083/debug/position
curl http://127.0.0.1:8083/debug/execution-report
curl http://127.0.0.1:8083/debug/orders
```

7. Recommended rollout:
- `DRY_RUN`: signal only, no position state
- `PAPER`: simulated fills and local position lifecycle
- `LIVE`: set `STRATEGY_LIVE_TRADING=true` and restart

8. Audit logs are written under the same log directory:
- `app.log`
- `signals.log`
- `orders.log`
- `fills.log`
