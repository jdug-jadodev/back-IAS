package com.backend_IAS.demo.exception.database;

import com.backend_IAS.demo.exception.message.InfrastructureMessages;
import java.io.Serial;

public class PersistenceTimeoutException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public PersistenceTimeoutException(Throwable cause) {
        super(InfrastructureMessages.DATABASE_TIMEOUT, cause);
    }
}
