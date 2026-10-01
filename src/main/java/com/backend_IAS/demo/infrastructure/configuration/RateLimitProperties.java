package com.backend_IAS.demo.infrastructure.configuration;

import com.backend_IAS.demo.exception.message.InfrastructureMessages;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private long capacity = 30;
    private Duration refillPeriod = Duration.ofMinutes(1);
    private long maxClients = 10000;

    public void validate() {
        if (capacity <= 0 || maxClients <= 0 || refillPeriod == null
                || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException(InfrastructureMessages.RATE_LIMIT_CONFIGURATION_INVALID);
        }
    }
}
