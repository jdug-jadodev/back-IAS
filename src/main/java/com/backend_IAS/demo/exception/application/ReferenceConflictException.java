package com.backend_IAS.demo.exception.application;

import java.io.Serial;

public class ReferenceConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReferenceConflictException(String applicationReference) {
        super("La referencia " + applicationReference + " ya está asociada a una solicitud con datos diferentes");
    }
}
