package com.backend_IAS.demo.exception.application;

import java.io.Serial;

public class InvalidApplicationDataException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidApplicationDataException(String message) {
        super(message);
    }
}
