package com.backend_IAS.demo.exception.message;

public final class DomainMessages {

    public static final String INVALID_AMOUNT = "El monto debe ser mayor que cero";
    public static final String INVALID_TERM = "El plazo debe estar entre 6 y 60 meses";
    public static final String CUSTOMER_NOT_FOUND = "El cliente no existe";
    public static final String CUSTOMER_BLOCKED = "El cliente no está habilitado";
    public static final String INSUFFICIENT_LIMIT = "El cupo disponible es insuficiente";

    public static final String DATA_REQUIRED = "data is required";
    public static final String CUSTOMER_REQUIRED = "customer is required";
    public static final String TOTAL_APPROVED_REQUIRED = "totalApproved is required";
    public static final String AMOUNT_REQUIRED = "amount is required";
    public static final String TERM_MONTHS_REQUIRED = "termMonths is required";
    public static final String CUSTOMER_STATUS_REQUIRED = "customer status is required";
    public static final String CREDIT_LIMIT_REQUIRED = "creditLimit is required";
    public static final String ORIGINAL_REQUIRED = "original is required";
    public static final String DECISION_REQUIRED = "decision is required";
    public static final String REASON_REQUIRED = "reason is required";
    public static final String APPLICATION_REQUIRED = "application is required";
    public static final String PAGE_CONTENT_REQUIRED = "page content is required";
    public static final String PAGINATION_INVALID = "Page, size and total elements must be valid";
    public static final String CUSTOMER_APPLICATION_MISMATCH = "Customer does not match application";
    public static final String NEGATIVE_APPROVED_TOTAL_OR_CREDIT_LIMIT =
            "Approved total and credit limit must not be negative";

    private DomainMessages() {
    }
}
