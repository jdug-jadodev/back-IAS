package com.backend_IAS.demo.exception.application;

import java.io.Serial;

public class ApplicationNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ApplicationNotFoundException(String applicationReference) {
        super("No se encontró la solicitud con referencia: " + applicationReference);
    }
}
