package com.backend_IAS.demo.exception.database;

import java.io.Serial;

public class DuplicateReferenceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateReferenceException(Throwable cause) {
        super("Ya existe una solicitud con la referencia indicada", cause);
    }
}
