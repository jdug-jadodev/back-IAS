package com.backend_IAS.demo.infrastructure.routerhandler.error;

import com.backend_IAS.demo.application.dto.ErrorResponseDto;
import com.backend_IAS.demo.exception.application.ApplicationNotFoundException;
import com.backend_IAS.demo.exception.application.CustomerNotFoundException;
import com.backend_IAS.demo.exception.application.RateLimitExceededException;
import com.backend_IAS.demo.exception.database.PersistenceTimeoutException;
import com.backend_IAS.demo.exception.application.InvalidApplicationDataException;
import com.backend_IAS.demo.exception.application.IdempotencyConflictException;
import com.backend_IAS.demo.exception.message.ErrorCodes;
import com.backend_IAS.demo.exception.message.InfrastructureMessages;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(-2)
@RequiredArgsConstructor
@Slf4j
public class GlobalErrorHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }

        String traceId = UUID.randomUUID().toString();
        HttpError httpError = describeError(error);
        if (httpError.getStatus().is5xxServerError()) {
            log.error(InfrastructureMessages.HTTP_REQUEST_FAILED_LOG,
                    traceId, exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(),
                    httpError.getStatus().value(), error);
        }

        ErrorResponseDto response = ErrorResponseDto.builder()
                .code(httpError.getCode())
                .message(httpError.getMessage())
                .traceId(traceId)
                .build();

        return Mono.fromCallable(() -> objectMapper.writeValueAsBytes(response))
                .flatMap(bytes -> {
                    exchange.getResponse().setStatusCode(httpError.getStatus());
                    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    exchange.getResponse().getHeaders().set(InfrastructureMessages.TRACE_ID_HEADER, traceId);
                    if (error instanceof RateLimitExceededException rateLimitError) {
                        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER,
                                Long.toString(rateLimitError.getRetryAfterSeconds()));
                    }
                    return exchange.getResponse().writeWith(Mono.just(
                            exchange.getResponse().bufferFactory().wrap(bytes)));
                });
    }

    private HttpError describeError(Throwable error) {
        if (error instanceof PersistenceTimeoutException) {
            return new HttpError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.DATABASE_TIMEOUT,
                    InfrastructureMessages.DATABASE_TIMEOUT);
        }
        if (error instanceof RateLimitExceededException) {
            return new HttpError(HttpStatus.TOO_MANY_REQUESTS, ErrorCodes.RATE_LIMIT_EXCEEDED,
                    InfrastructureMessages.RATE_LIMIT_EXCEEDED);
        }
        if (error instanceof InvalidApplicationDataException) {
            return new HttpError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_APPLICATION_DATA, error.getMessage());
        }
        if (error instanceof ApplicationNotFoundException) {
            return new HttpError(HttpStatus.NOT_FOUND, ErrorCodes.APPLICATION_NOT_FOUND, error.getMessage());
        }
        if (error instanceof CustomerNotFoundException) {
            return new HttpError(HttpStatus.NOT_FOUND, ErrorCodes.CUSTOMER_NOT_FOUND, error.getMessage());
        }
        if (error instanceof IdempotencyConflictException) {
            return new HttpError(HttpStatus.CONFLICT, ErrorCodes.IDEMPOTENCY_CONFLICT, error.getMessage());
        }
        if (error instanceof DataBufferLimitException) {
            return describeHttpStatus(HttpStatus.CONTENT_TOO_LARGE);
        }
        if (error instanceof ServerWebInputException) {
            return new HttpError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_REQUEST,
                    InfrastructureMessages.INVALID_BODY_OR_PARAMETERS);
        }
        if (error instanceof ResponseStatusException statusException) {
            return describeHttpStatus(statusException.getStatusCode());
        }
        return describeHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private HttpError describeHttpStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> new HttpError(status, ErrorCodes.INVALID_REQUEST, InfrastructureMessages.INVALID_REQUEST);
            case 404 -> new HttpError(status, ErrorCodes.RESOURCE_NOT_FOUND, InfrastructureMessages.RESOURCE_NOT_FOUND);
            case 405 -> new HttpError(status, ErrorCodes.METHOD_NOT_ALLOWED, InfrastructureMessages.METHOD_NOT_ALLOWED);
            case 406 -> new HttpError(status, ErrorCodes.NOT_ACCEPTABLE, InfrastructureMessages.NOT_ACCEPTABLE);
            case 409 -> new HttpError(status, ErrorCodes.IDEMPOTENCY_CONFLICT, InfrastructureMessages.IDEMPOTENCY_CONFLICT);
            case 413 -> new HttpError(status, ErrorCodes.PAYLOAD_TOO_LARGE, InfrastructureMessages.PAYLOAD_TOO_LARGE);
            case 415 -> new HttpError(status, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, InfrastructureMessages.UNSUPPORTED_MEDIA_TYPE);
            default -> new HttpError(status, status.is5xxServerError() ? ErrorCodes.INTERNAL_ERROR : ErrorCodes.HTTP_ERROR,
                    InfrastructureMessages.REQUEST_PROCESSING_FAILED);
        };
    }

    @Getter
    @RequiredArgsConstructor
    private static final class HttpError {
        private final HttpStatusCode status;
        private final String code;
        private final String message;
    }
}
