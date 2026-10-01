package com.backend_IAS.demo.exception.application;

import com.backend_IAS.demo.exception.message.ApplicationMessages;
import java.io.Serial;

public class CustomerNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CustomerNotFoundException(String customerId) {
        super(ApplicationMessages.CUSTOMER_NOT_FOUND.formatted(customerId));
    }
}
