package com.backend_IAS.demo.application.validation;

import com.backend_IAS.demo.application.dto.ApplicationDataDto;
import com.backend_IAS.demo.exception.application.InvalidApplicationDataException;
import com.backend_IAS.demo.exception.message.ValidationMessages;
import org.springframework.stereotype.Component;

@Component
public final class ApplicationValidator {

    private static final int MIN_RECENT_LIMIT = 1;
    private static final int MAX_RECENT_LIMIT = 100;

    public void validate(ApplicationDataDto data) {
        if (data == null) {
            throw new InvalidApplicationDataException(ValidationMessages.APPLICATION_DATA_REQUIRED);
        }
        validateReference(data.getApplicationReference());
        if (data.getCustomerId() == null || data.getCustomerId().isBlank()) {
            throw new InvalidApplicationDataException(ValidationMessages.CUSTOMER_ID_REQUIRED);
        }
        if (data.getAmount() == null) {
            throw new InvalidApplicationDataException(ValidationMessages.AMOUNT_REQUIRED);
        }
        if (data.getTermMonths() == null) {
            throw new InvalidApplicationDataException(ValidationMessages.TERM_MONTHS_REQUIRED);
        }
    }

    public void validateReference(String applicationReference) {
        if (applicationReference == null || applicationReference.isBlank()) {
            throw new InvalidApplicationDataException(ValidationMessages.APPLICATION_REFERENCE_REQUIRED);
        }
    }

    public void validateLimit(int limit) {
        if (limit < MIN_RECENT_LIMIT || limit > MAX_RECENT_LIMIT) {
            throw new InvalidApplicationDataException(ValidationMessages.RECENT_LIMIT_OUT_OF_RANGE);
        }
    }
}
