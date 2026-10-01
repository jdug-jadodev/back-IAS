package com.backend_IAS.demo.exception.application;

import java.io.Serial;

public class CustomerNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CustomerNotFoundException(String customerId) {
        super("No se encontró el cliente con identificador: " + customerId);
    }
}
