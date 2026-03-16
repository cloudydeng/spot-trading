package com.matching.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matching.trading.config.BinanceProperties;
import com.matching.trading.model.DepthLevel;
import com.matching.trading.model.DepthSnapshot;
import com.matching.trading.model.ExchangeFilters;
import com.matching.trading.model.MarketOrderResult;
import com.matching.trading.model.ApiErrorResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BinanceRestClient {
    private static final Logger log = LoggerFactory.getLogger(BinanceRestClient.class);

    private final BinanceProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BinanceRestClient(BinanceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getMarket().getRestTimeoutMs()))
            .build();
    }

    public DepthSnapshot fetchDepthSnapshot() {
        String url = properties.getBaseUrl() + "/api/v3/depth?symbol=" + properties.getSymbol()
            + "&limit=" + properties.getMarket().getSnapshotLimit();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .GET()
            .timeout(Duration.ofMillis(properties.getMarket().getRestTimeoutMs()))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response);
            JsonNode root = objectMapper.readTree(response.body());
            return new DepthSnapshot(
                root.path("lastUpdateId").asLong(),
                parseLevels(root.path("bids")),
                parseLevels(root.path("asks"))
            );
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to fetch depth snapshot", e);
        }
    }

    public ExchangeFilters fetchExchangeFilters() {
        String url = properties.getBaseUrl() + "/api/v3/exchangeInfo?symbol=" + properties.getSymbol();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .GET()
            .timeout(Duration.ofMillis(properties.getMarket().getRestTimeoutMs()))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response);
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode filters = root.path("symbols").path(0).path("filters");
            BigDecimal tickSize = BigDecimal.ZERO;
            BigDecimal stepSize = BigDecimal.ZERO;
            BigDecimal minQty = BigDecimal.ZERO;
            BigDecimal minNotional = BigDecimal.ZERO;
            for (JsonNode filter : filters) {
                String filterType = filter.path("filterType").asText();
                if ("PRICE_FILTER".equals(filterType)) {
                    tickSize = new BigDecimal(filter.path("tickSize").asText("0"));
                } else if ("LOT_SIZE".equals(filterType)) {
                    stepSize = new BigDecimal(filter.path("stepSize").asText("0"));
                    minQty = new BigDecimal(filter.path("minQty").asText("0"));
                } else if ("MIN_NOTIONAL".equals(filterType) || "NOTIONAL".equals(filterType)) {
                    minNotional = new BigDecimal(filter.path("minNotional").asText("0"));
                }
            }
            return new ExchangeFilters(tickSize, stepSize, minQty, minNotional);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to fetch exchange filters", e);
        }
    }

    public MarketOrderResult placeMarketBuy(BigDecimal quoteOrderQty) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
            || properties.getApiSecret() == null || properties.getApiSecret().isBlank()) {
            throw new IllegalStateException("Binance API credentials are not configured");
        }

        String query = "symbol=" + encode(properties.getSymbol())
            + "&side=BUY"
            + "&type=MARKET"
            + "&quoteOrderQty=" + encode(quoteOrderQty.stripTrailingZeros().toPlainString())
            + "&newOrderRespType=FULL"
            + "&recvWindow=" + properties.getMarket().getRecvWindowMs()
            + "&timestamp=" + System.currentTimeMillis();
        return executeSignedOrder(query, "BUY");
    }

    public MarketOrderResult placeMarketSell(BigDecimal quantity) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
            || properties.getApiSecret() == null || properties.getApiSecret().isBlank()) {
            throw new IllegalStateException("Binance API credentials are not configured");
        }

        String query = "symbol=" + encode(properties.getSymbol())
            + "&side=SELL"
            + "&type=MARKET"
            + "&quantity=" + encode(quantity.stripTrailingZeros().toPlainString())
            + "&newOrderRespType=FULL"
            + "&recvWindow=" + properties.getMarket().getRecvWindowMs()
            + "&timestamp=" + System.currentTimeMillis();
        return executeSignedOrder(query, "SELL");
    }

    public String createUserDataListenKey() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + "/api/v3/userDataStream"))
            .header("X-MBX-APIKEY", properties.getApiKey())
            .POST(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofMillis(properties.getMarket().getRestTimeoutMs()))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response);
            return objectMapper.readTree(response.body()).path("listenKey").asText();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to create listen key", e);
        }
    }

    public void keepAliveUserDataListenKey(String listenKey) {
        String body = "listenKey=" + encode(listenKey);
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + "/api/v3/userDataStream"))
            .header("X-MBX-APIKEY", properties.getApiKey())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .method("PUT", HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofMillis(properties.getMarket().getRestTimeoutMs()))
            .build();
        sendWithoutBody(request, "Failed to keep alive listen key");
    }

    public void closeUserDataListenKey(String listenKey) {
        String body = "listenKey=" + encode(listenKey);
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + "/api/v3/userDataStream"))
            .header("X-MBX-APIKEY", properties.getApiKey())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .method("DELETE", HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofMillis(properties.getMarket().getRestTimeoutMs()))
            .build();
        sendWithoutBody(request, "Failed to close listen key");
    }

    private MarketOrderResult executeSignedOrder(String query, String side) {
        String signature = hmacSha256(query, properties.getApiSecret());

        HttpRequest request = HttpRequest.newBuilder(
                URI.create(properties.getBaseUrl() + "/api/v3/order?" + query + "&signature=" + signature))
            .header("X-MBX-APIKEY", properties.getApiKey())
            .POST(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofMillis(properties.getMarket().getRestTimeoutMs()))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response);
            JsonNode root = objectMapper.readTree(response.body());
            BigDecimal executedQty = new BigDecimal(root.path("executedQty").asText("0"));
            BigDecimal cumulativeQuoteQty = new BigDecimal(root.path("cummulativeQuoteQty").asText("0"));
            BigDecimal averagePrice = executedQty.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : cumulativeQuoteQty.divide(executedQty, java.math.MathContext.DECIMAL64);
            MarketOrderResult result = new MarketOrderResult(
                root.path("symbol").asText(properties.getSymbol()),
                side,
                root.path("status").asText("UNKNOWN"),
                root.path("orderId").asLong(),
                executedQty,
                cumulativeQuoteQty,
                averagePrice,
                response.body()
            );
            log.info("Binance market order response: {}", result);
            return result;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to place market order", e);
        }
    }

    private void ensureSuccess(HttpResponse<String> response) throws IOException {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        JsonNode root = objectMapper.readTree(response.body());
        int code = root.path("code").asInt(response.statusCode());
        String message = root.path("msg").asText(response.body());
        boolean retryable = response.statusCode() == 429
            || response.statusCode() == 418
            || response.statusCode() >= 500;
        throw new BinanceApiException(new ApiErrorResponse(response.statusCode(), code, message, retryable));
    }

    private void sendWithoutBody(HttpRequest request, String errorMessage) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(errorMessage, e);
        }
    }

    private List<DepthLevel> parseLevels(JsonNode levelsNode) {
        List<DepthLevel> levels = new ArrayList<>();
        for (JsonNode node : levelsNode) {
            levels.add(new DepthLevel(
                new BigDecimal(node.path(0).asText("0")),
                new BigDecimal(node.path(1).asText("0"))
            ));
        }
        return levels;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to sign Binance request", e);
        }
    }
}
