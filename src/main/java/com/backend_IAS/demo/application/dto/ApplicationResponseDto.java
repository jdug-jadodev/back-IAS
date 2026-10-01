package com.backend_IAS.demo.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class ApplicationResponseDto {
    @JsonProperty("applicationReference")
    @Schema(example = "SWAGGER-001")
    private final String applicationReference;

    @JsonProperty("customerId")
    @Schema(example = "CLI-1001")
    private final String customerId;

    @JsonProperty("amount")
    @Schema(description = "Monto expresado como texto decimal", example = "1000000.00")
    private final String amount;

    @JsonProperty("termMonths")
    @Schema(example = "12")
    private final Integer termMonths;

    @JsonProperty("status")
    @Schema(allowableValues = {"APPROVED", "REJECTED"}, example = "APPROVED")
    private final String status;

    @JsonProperty("message")
    @Schema(description = "Indica si se procesó la solicitud o si ya había sido aprobada o rechazada",
            example = "Esta solicitud fue aprobada")
    private final String message;

    @JsonProperty("processedAt")
    @Schema(description = "Fecha original del procesamiento; se conserva en los reintentos",
            example = "2026-10-01T12:00:00Z")
    private final Instant processedAt;

    @JsonProperty("reasonCode")
    @Schema(description = "Código del motivo de rechazo; nulo para aprobaciones", nullable = true,
            allowableValues = {"INVALID_AMOUNT", "INVALID_TERM", "CUSTOMER_NOT_FOUND", "CUSTOMER_BLOCKED", "INSUFFICIENT_LIMIT"})
    private final String reasonCode;

    @JsonProperty("reason")
    @Schema(description = "Explicación del rechazo; nula para aprobaciones", nullable = true)
    private final String reason;
}
