package com.backend_IAS.demo.exception.message;

public final class ValidationMessages {

    public static final String APPLICATION_DATA_REQUIRED = "Los datos de la solicitud son obligatorios";
    public static final String IDEMPOTENCY_KEY_INVALID = "La cabecera Idempotency-Key es obligatoria y debe contener un único UUID válido";
    public static final String APPLICATION_REFERENCE_REQUIRED = "La referencia de la solicitud es obligatoria";
    public static final String CUSTOMER_ID_REQUIRED = "El identificador del cliente es obligatorio";
    public static final String AMOUNT_REQUIRED = "El monto de la solicitud es obligatorio";
    public static final String TERM_MONTHS_REQUIRED = "El plazo de la solicitud es obligatorio";
    public static final String PAGE_OUT_OF_RANGE = "La página debe ser mayor o igual a cero";
    public static final String PAGE_SIZE_OUT_OF_RANGE = "El tamaño de página debe estar entre 1 y 100";
    public static final String LEGACY_LIMIT_NOT_SUPPORTED = "El parámetro limit ya no está disponible; utiliza page y size";

    private ValidationMessages() {
    }
}
