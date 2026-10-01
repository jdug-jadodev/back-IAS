package com.backend_IAS.demo.exception.application;

import com.backend_IAS.demo.exception.message.ApplicationMessages;
import java.io.Serial;

public class ApplicationNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ApplicationNotFoundException(String applicationReference) {
        super(ApplicationMessages.APPLICATION_NOT_FOUND.formatted(applicationReference));
    }
}
