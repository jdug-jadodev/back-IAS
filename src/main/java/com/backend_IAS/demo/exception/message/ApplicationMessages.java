package com.backend_IAS.demo.exception.message;

public final class ApplicationMessages {

    public static final String APPLICATION_APPROVED = "Esta solicitud fue aprobada";
    public static final String APPLICATION_REJECTED = "Esta solicitud fue rechazada";
    public static final String APPLICATION_ALREADY_APPROVED = "Esta solicitud ya fue aprobada";
    public static final String APPLICATION_ALREADY_REJECTED = "Esta solicitud ya fue rechazada";
    public static final String APPLICATION_NOT_FOUND = "No se encontró la solicitud con referencia: %s";
    public static final String CUSTOMER_NOT_FOUND = "No se encontró el cliente con identificador: %s";
    public static final String IDEMPOTENCY_CONFLICT =
            "La clave de idempotencia %s ya está asociada a una solicitud con datos diferentes";

    private ApplicationMessages() {
    }
}
