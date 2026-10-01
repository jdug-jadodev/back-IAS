package com.backend_IAS.demo.infrastructure.configuration;

import com.backend_IAS.demo.infrastructure.ratelimit.ClientRateLimiter;
import io.github.bucket4j.TimeMeter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ClientRateLimiter clientRateLimiter(RateLimitProperties properties) {
        return new ClientRateLimiter(properties, TimeMeter.SYSTEM_NANOTIME);
    }
}
