package com.backend_IAS.demo.exception.application;

import com.backend_IAS.demo.exception.message.ApplicationMessages;
import java.io.Serial;

public class ReferenceConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReferenceConflictException(String applicationReference) {
        super(ApplicationMessages.REFERENCE_CONFLICT.formatted(applicationReference));
    }
}
