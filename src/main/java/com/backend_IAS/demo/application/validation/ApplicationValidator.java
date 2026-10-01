package com.backend_IAS.demo.application.validation;

import com.backend_IAS.demo.exception.application.InvalidApplicationDataException;

public final class ApplicationValidator {

    private static final int MIN_RECENT_LIMIT = 1;
    private static final int MAX_RECENT_LIMIT = 100;

    public void validateReference(String applicationReference) {
        if (applicationReference == null || applicationReference.isBlank()) {
            throw new InvalidApplicationDataException("La referencia de la solicitud es obligatoria");
        }
    }

    public void validateLimit(int limit) {
        if (limit < MIN_RECENT_LIMIT || limit > MAX_RECENT_LIMIT) {
            throw new InvalidApplicationDataException("El límite debe estar entre 1 y 100");
        }
    }
}
