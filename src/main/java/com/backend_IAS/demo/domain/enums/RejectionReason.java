package com.backend_IAS.demo.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RejectionReason {
    INVALID_AMOUNT("INVALID_AMOUNT", "El monto debe ser mayor que cero"),
    INVALID_TERM("INVALID_TERM", "El plazo debe estar entre 6 y 60 meses"),
    CUSTOMER_NOT_FOUND("CUSTOMER_NOT_FOUND", "El cliente no existe"),
    CUSTOMER_BLOCKED("CUSTOMER_BLOCKED", "El cliente no está habilitado"),
    INSUFFICIENT_LIMIT("INSUFFICIENT_LIMIT", "El cupo disponible es insuficiente");

    private final String code;
    private final String description;
}
