package com.backend_IAS.demo.infrastructure.routerhandler.error;

import com.backend_IAS.demo.application.dto.ErrorResponseDto;
import com.backend_IAS.demo.exception.application.ApplicationNotFoundException;
import com.backend_IAS.demo.exception.application.InvalidApplicationDataException;
import com.backend_IAS.demo.exception.application.ReferenceConflictException;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
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
            log.error("HTTP request failed: traceId={}, method={}, path={}, status={}",
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
                    exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
                    return exchange.getResponse().writeWith(Mono.just(
                            exchange.getResponse().bufferFactory().wrap(bytes)));
                });
    }

    private HttpError describeError(Throwable error) {
        if (error instanceof InvalidApplicationDataException) {
            return new HttpError(HttpStatus.BAD_REQUEST, "INVALID_APPLICATION_DATA", error.getMessage());
        }
        if (error instanceof ApplicationNotFoundException) {
            return new HttpError(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", error.getMessage());
        }
        if (error instanceof ReferenceConflictException) {
            return new HttpError(HttpStatus.CONFLICT, "REFERENCE_CONFLICT", error.getMessage());
        }
        if (error instanceof DataBufferLimitException) {
            return describeHttpStatus(HttpStatus.CONTENT_TOO_LARGE);
        }
        if (error instanceof ServerWebInputException) {
            return new HttpError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "El cuerpo o los parámetros de la petición no son válidos");
        }
        if (error instanceof ResponseStatusException statusException) {
            return describeHttpStatus(statusException.getStatusCode());
        }
        return describeHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private HttpError describeHttpStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> new HttpError(status, "INVALID_REQUEST", "La petición contiene datos inválidos");
            case 404 -> new HttpError(status, "RESOURCE_NOT_FOUND", "El recurso solicitado no existe");
            case 405 -> new HttpError(status, "METHOD_NOT_ALLOWED", "El método HTTP no está permitido para esta ruta");
            case 406 -> new HttpError(status, "NOT_ACCEPTABLE", "El formato de respuesta solicitado no está disponible");
            case 409 -> new HttpError(status, "REFERENCE_CONFLICT", "La referencia está asociada a datos diferentes");
            case 413 -> new HttpError(status, "PAYLOAD_TOO_LARGE", "El cuerpo de la petición supera el tamaño permitido");
            case 415 -> new HttpError(status, "UNSUPPORTED_MEDIA_TYPE", "El tipo de contenido de la petición no está soportado");
            default -> new HttpError(status, status.is5xxServerError() ? "INTERNAL_ERROR" : "HTTP_ERROR",
                    "No fue posible procesar la petición");
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
