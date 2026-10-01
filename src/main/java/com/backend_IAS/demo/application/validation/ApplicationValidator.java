package com.backend_IAS.demo.application.validation;

import com.backend_IAS.demo.application.dto.ApplicationDataDto;
import com.backend_IAS.demo.exception.application.InvalidApplicationDataException;
import com.backend_IAS.demo.exception.message.ValidationMessages;
import org.springframework.stereotype.Component;

@Component
public final class ApplicationValidator {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    public void validate(ApplicationDataDto data) {
        if (data == null) {
            throw new InvalidApplicationDataException(ValidationMessages.APPLICATION_DATA_REQUIRED);
        }
        if (data.getIdempotencyKey() == null || !data.getIdempotencyKey().matches(
                "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
            throw new InvalidApplicationDataException(ValidationMessages.IDEMPOTENCY_KEY_INVALID);
        }
        validateCustomerId(data.getCustomerId());
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

    public void validateCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new InvalidApplicationDataException(ValidationMessages.CUSTOMER_ID_REQUIRED);
        }
    }

    public void validatePagination(int page, int size) {
        if (page < 0) {
            throw new InvalidApplicationDataException(ValidationMessages.PAGE_OUT_OF_RANGE);
        }
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new InvalidApplicationDataException(ValidationMessages.PAGE_SIZE_OUT_OF_RANGE);
        }
    }
}
