package com.backend_IAS.demo.domain.enums;

import com.backend_IAS.demo.exception.message.DomainMessages;
import com.backend_IAS.demo.exception.message.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RejectionReason {
    INVALID_AMOUNT(ErrorCodes.INVALID_AMOUNT, DomainMessages.INVALID_AMOUNT),
    INVALID_TERM(ErrorCodes.INVALID_TERM, DomainMessages.INVALID_TERM),
    CUSTOMER_NOT_FOUND(ErrorCodes.CUSTOMER_NOT_FOUND, DomainMessages.CUSTOMER_NOT_FOUND),
    CUSTOMER_BLOCKED(ErrorCodes.CUSTOMER_BLOCKED, DomainMessages.CUSTOMER_BLOCKED),
    INSUFFICIENT_LIMIT(ErrorCodes.INSUFFICIENT_LIMIT, DomainMessages.INSUFFICIENT_LIMIT);

    private final String code;
    private final String description;
}
