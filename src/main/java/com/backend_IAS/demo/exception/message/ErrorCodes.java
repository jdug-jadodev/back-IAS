package com.backend_IAS.demo.exception.message;

public final class ErrorCodes {

    public static final String INVALID_APPLICATION_DATA = "INVALID_APPLICATION_DATA";
    public static final String APPLICATION_NOT_FOUND = "APPLICATION_NOT_FOUND";
    public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String NOT_ACCEPTABLE = "NOT_ACCEPTABLE";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String DATABASE_TIMEOUT = "DATABASE_TIMEOUT";
    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final String HTTP_ERROR = "HTTP_ERROR";

    public static final String INVALID_AMOUNT = "INVALID_AMOUNT";
    public static final String INVALID_TERM = "INVALID_TERM";
    public static final String CUSTOMER_NOT_FOUND = "CUSTOMER_NOT_FOUND";
    public static final String CUSTOMER_BLOCKED = "CUSTOMER_BLOCKED";
    public static final String INSUFFICIENT_LIMIT = "INSUFFICIENT_LIMIT";

    private ErrorCodes() {
    }
}
