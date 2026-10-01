package com.backend_IAS.demo.exception.database;

import com.backend_IAS.demo.exception.message.InfrastructureMessages;
import java.io.Serial;

public class PersistenceFailureException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PersistenceFailureException(Throwable cause) {
        super(InfrastructureMessages.PERSISTENCE_FAILURE, cause);
    }
}
