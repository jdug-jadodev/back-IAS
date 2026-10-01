package com.backend_IAS.demo.infrastructure.ratelimit;

import com.backend_IAS.demo.infrastructure.configuration.RateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;

public class ClientRateLimiter {
    private final Cache<String, Bucket> buckets;
    private final long capacity;
    private final Duration refillPeriod;
    private final TimeMeter timeMeter;

    public ClientRateLimiter(RateLimitProperties properties, TimeMeter timeMeter) {
        properties.validate();
        capacity = properties.getCapacity();
        refillPeriod = properties.getRefillPeriod();
        this.timeMeter = timeMeter;
        Duration retention = refillPeriod.multipliedBy(2);
        if (retention.compareTo(Duration.ofMinutes(10)) < 0) {
            retention = Duration.ofMinutes(10);
        }
        buckets = Caffeine.newBuilder().maximumSize(properties.getMaxClients())
                .expireAfterAccess(retention).build();
    }

    public ConsumptionProbe tryConsume(String clientAddress) {
        return buckets.get(clientAddress, address -> Bucket.builder().withCustomTimePrecision(timeMeter)
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, refillPeriod))
                .build()).tryConsumeAndReturnRemaining(1);
    }
}
