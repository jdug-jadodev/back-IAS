package com.backend_IAS.demo.infrastructure.ratelimit;

import com.backend_IAS.demo.exception.application.RateLimitExceededException;
import com.backend_IAS.demo.exception.message.InfrastructureMessages;
import io.github.bucket4j.ConsumptionProbe;
import java.net.InetSocketAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

@Component
@Order(-1)
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ApplicationRateLimitFilter implements WebFilter {
    private static final PathPattern APPLICATION_PATH = PathPatternParser.defaultInstance.parse("/applications");
    private final ClientRateLimiter rateLimiter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            if (exchange.getRequest().getMethod() != HttpMethod.POST
                    || !APPLICATION_PATH.matches(exchange.getRequest().getPath().pathWithinApplication())) {
                return chain.filter(exchange);
            }
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            String key = remoteAddress == null || remoteAddress.getAddress() == null
                    ? InfrastructureMessages.UNKNOWN_REMOTE_ADDRESS : remoteAddress.getAddress().getHostAddress();
            ConsumptionProbe probe = rateLimiter.tryConsume(key);
            if (probe.isConsumed()) {
                return chain.filter(exchange);
            }
            long retryAfterSeconds = Math.max(1, (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0));
            return Mono.error(new RateLimitExceededException(retryAfterSeconds));
        });
    }
}
