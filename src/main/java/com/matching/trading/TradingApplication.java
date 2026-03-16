package com.matching.trading;

import com.matching.trading.config.BinanceProperties;
import com.matching.trading.config.OrderBookProperties;
import com.matching.trading.config.StrategyProperties;
import com.matching.trading.config.TradingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(
    scanBasePackages = "com.matching.trading",
    exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        RedisAutoConfiguration.class
    }
)
@ConfigurationPropertiesScan(basePackageClasses = {
    BinanceProperties.class,
    StrategyProperties.class,
    OrderBookProperties.class,
    TradingProperties.class
})
public class TradingApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradingApplication.class, args);
    }
}
