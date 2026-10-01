package com.backend_IAS.demo.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class CustomerCreditSummaryResponseDto {
    @JsonProperty("customerId")
    @Schema(example = "CLI-1001")
    private final String customerId;
    @JsonProperty("status")
    @Schema(allowableValues = {"ELIGIBLE", "BLOCKED"}, example = "ELIGIBLE")
    private final String status;
    @JsonProperty("creditLimit")
    @Schema(description = "Cupo máximo acumulado, como texto decimal", example = "10000000")
    private final String creditLimit;
    @JsonProperty("approvedAmount")
    @Schema(description = "Suma de solicitudes aprobadas, como texto decimal", example = "6000000")
    private final String approvedAmount;
    @JsonProperty("availableAmount")
    @Schema(description = "Saldo informativo, nunca negativo; no reserva cupo", example = "4000000")
    private final String availableAmount;
}
