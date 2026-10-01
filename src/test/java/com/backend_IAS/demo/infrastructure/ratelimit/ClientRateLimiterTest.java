package com.backend_IAS.demo.infrastructure.ratelimit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.backend_IAS.demo.infrastructure.configuration.RateLimitProperties;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class ClientRateLimiterTest {
    @Test
    void shouldRefillQuotaAndKeepClientsIndependent() {
        AtomicLong nanos = new AtomicLong();
        TimeMeter timeMeter = new TimeMeter() {
            @Override
            public long currentTimeNanos() {
                return nanos.get();
            }

            @Override
            public boolean isWallClockBased() {
                return false;
            }
        };
        RateLimitProperties properties = new RateLimitProperties();
        properties.setCapacity(2);
        properties.setRefillPeriod(Duration.ofSeconds(1));
        ClientRateLimiter limiter = new ClientRateLimiter(properties, timeMeter);

        assertTrue(limiter.tryConsume("first").isConsumed());
        assertTrue(limiter.tryConsume("first").isConsumed());
        assertFalse(limiter.tryConsume("first").isConsumed());
        assertEquals(500_000_000L, limiter.tryConsume("first").getNanosToWaitForRefill());
        assertTrue(limiter.tryConsume("second").isConsumed());
        nanos.addAndGet(500_000_000L);
        assertTrue(limiter.tryConsume("first").isConsumed());
        assertFalse(limiter.tryConsume("first").isConsumed());
    }

    @Test
    void shouldEnforceQuotaAtomicallyForConcurrentRequests() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setCapacity(30);
        properties.setRefillPeriod(Duration.ofDays(1));
        ClientRateLimiter limiter = new ClientRateLimiter(properties, TimeMeter.SYSTEM_NANOTIME);

        StepVerifier.create(Flux.range(0, 100).flatMap(index ->
                        Mono.fromSupplier(() -> limiter.tryConsume("same-client").isConsumed())
                                .subscribeOn(Schedulers.parallel()))
                .filter(Boolean::booleanValue).count())
                .expectNext(30L).verifyComplete();
    }
}
