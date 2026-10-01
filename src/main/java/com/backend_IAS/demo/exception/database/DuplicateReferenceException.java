package com.backend_IAS.demo.exception.database;

import com.backend_IAS.demo.exception.message.InfrastructureMessages;
import java.io.Serial;

public class DuplicateReferenceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateReferenceException(Throwable cause) {
        super(InfrastructureMessages.DUPLICATE_REFERENCE, cause);
    }
}
