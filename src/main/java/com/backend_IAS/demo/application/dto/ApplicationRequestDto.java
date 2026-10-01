package com.backend_IAS.demo.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationRequestDto {
    @JsonProperty("customerId")
    @Schema(description = "Identificador del cliente", example = "CLI-1001",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerId;

    @JsonProperty("amount")
    @Schema(description = "Monto decimal; cero y valores negativos producen un rechazo de negocio",
            type = "string", example = "1000000.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    @JsonProperty("termMonths")
    @Schema(description = "Plazo en meses; valores fuera de 6 a 60 producen un rechazo de negocio",
            example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer termMonths;
}
