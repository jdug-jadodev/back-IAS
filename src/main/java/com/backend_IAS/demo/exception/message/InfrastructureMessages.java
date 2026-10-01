package com.backend_IAS.demo.exception.message;

public final class InfrastructureMessages {

    public static final String INVALID_JSON_BODY =
            "El cuerpo debe contener un JSON válido con los tipos de datos esperados";
    public static final String REQUEST_BODY_REQUIRED = "El cuerpo de la solicitud es obligatorio";
    public static final String RECENT_LIMIT_MUST_BE_INTEGER = "El parámetro limit debe ser un número entero";
    public static final String INVALID_BODY_OR_PARAMETERS = "El cuerpo o los parámetros de la petición no son válidos";
    public static final String INVALID_REQUEST = "La petición contiene datos inválidos";
    public static final String RESOURCE_NOT_FOUND = "El recurso solicitado no existe";
    public static final String METHOD_NOT_ALLOWED = "El método HTTP no está permitido para esta ruta";
    public static final String NOT_ACCEPTABLE = "El formato de respuesta solicitado no está disponible";
    public static final String REFERENCE_CONFLICT = "La referencia está asociada a datos diferentes";
    public static final String PAYLOAD_TOO_LARGE = "El cuerpo de la petición supera el tamaño permitido";
    public static final String UNSUPPORTED_MEDIA_TYPE = "El tipo de contenido de la petición no está soportado";
    public static final String REQUEST_PROCESSING_FAILED = "No fue posible procesar la petición";
    public static final String DUPLICATE_REFERENCE = "Ya existe una solicitud con la referencia indicada";
    public static final String PERSISTENCE_FAILURE = "No fue posible completar la operación de base de datos";

    public static final String APPROVED_TOTAL_QUERY_EMPTY = "Approved total query did not return a value";
    public static final String APPLICATION_INSERT_EMPTY = "Insertion did not return the persisted application";
    public static final String HTTP_REQUEST_FAILED_LOG =
            "HTTP request failed: traceId={}, method={}, path={}, status={}";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private InfrastructureMessages() {
    }
}
