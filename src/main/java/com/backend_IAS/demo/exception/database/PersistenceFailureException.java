package com.backend_IAS.demo.exception.database;

import java.io.Serial;

public class PersistenceFailureException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PersistenceFailureException(Throwable cause) {
        super("No fue posible completar la operación de base de datos", cause);
    }
}
