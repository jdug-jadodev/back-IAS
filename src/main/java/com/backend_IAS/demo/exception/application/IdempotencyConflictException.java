package com.backend_IAS.demo.exception.application;

import com.backend_IAS.demo.exception.message.ApplicationMessages;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String idempotencyKey) {
        super(ApplicationMessages.IDEMPOTENCY_CONFLICT.formatted(idempotencyKey));
    }
}
