package com.backend_IAS.demo.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class ErrorResponseDto {

    @JsonProperty("code")
    @Schema(description = "Código del error", example = "INVALID_APPLICATION_DATA")
    private final String code;

    @JsonProperty("message")
    @Schema(description = "Descripción del error", example = "El monto de la solicitud es obligatorio")
    private final String message;

    @JsonProperty("traceId")
    @Schema(description = "Identificador de correlación; coincide con la cabecera X-Trace-Id",
            example = "7a0fd604-7f80-4bce-a2af-b840bcc3191e")
    private final String traceId;
}
