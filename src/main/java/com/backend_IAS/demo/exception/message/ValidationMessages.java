package com.backend_IAS.demo.exception.message;

public final class ValidationMessages {

    public static final String APPLICATION_DATA_REQUIRED = "Los datos de la solicitud son obligatorios";
    public static final String APPLICATION_REFERENCE_REQUIRED = "La referencia de la solicitud es obligatoria";
    public static final String CUSTOMER_ID_REQUIRED = "El identificador del cliente es obligatorio";
    public static final String AMOUNT_REQUIRED = "El monto de la solicitud es obligatorio";
    public static final String TERM_MONTHS_REQUIRED = "El plazo de la solicitud es obligatorio";
    public static final String RECENT_LIMIT_OUT_OF_RANGE = "El límite debe estar entre 1 y 100";

    private ValidationMessages() {
    }
}
