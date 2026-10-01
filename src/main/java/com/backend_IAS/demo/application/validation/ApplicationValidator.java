package com.backend_IAS.demo.application.validation;

import com.backend_IAS.demo.application.dto.ApplicationDataDto;
import com.backend_IAS.demo.exception.application.InvalidApplicationDataException;
import org.springframework.stereotype.Component;

@Component
public final class ApplicationValidator {

    private static final int MIN_RECENT_LIMIT = 1;
    private static final int MAX_RECENT_LIMIT = 100;

    public void validate(ApplicationDataDto data) {
        if (data == null) {
            throw new InvalidApplicationDataException("Los datos de la solicitud son obligatorios");
        }
        validateReference(data.getApplicationReference());
        if (data.getCustomerId() == null || data.getCustomerId().isBlank()) {
            throw new InvalidApplicationDataException("El identificador del cliente es obligatorio");
        }
        if (data.getAmount() == null) {
            throw new InvalidApplicationDataException("El monto de la solicitud es obligatorio");
        }
        if (data.getTermMonths() == null) {
            throw new InvalidApplicationDataException("El plazo de la solicitud es obligatorio");
        }
    }

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
