package com.backend_IAS.demo.exception.application;

import com.backend_IAS.demo.exception.message.InfrastructureMessages;
import java.io.Serial;
import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(InfrastructureMessages.RATE_LIMIT_EXCEEDED);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
