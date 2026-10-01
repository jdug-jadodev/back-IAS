package com.backend_IAS.demo.exception.database;

import com.backend_IAS.demo.exception.message.InfrastructureMessages;

public class DuplicateIdempotencyKeyException extends RuntimeException {
    public DuplicateIdempotencyKeyException(Throwable cause) {
        super(InfrastructureMessages.DUPLICATE_IDEMPOTENCY_KEY, cause);
    }
}
