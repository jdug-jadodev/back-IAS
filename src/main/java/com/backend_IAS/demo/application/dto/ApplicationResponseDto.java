package com.backend_IAS.demo.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class ApplicationResponseDto {
    @JsonProperty("applicationReference")
    private final String applicationReference;

    @JsonProperty("customerId")
    private final String customerId;

    @JsonProperty("amount")
    private final String amount;

    @JsonProperty("termMonths")
    private final Integer termMonths;

    @JsonProperty("status")
    private final String status;

    @JsonProperty("processedAt")
    private final Instant processedAt;

    @JsonProperty("reasonCode")
    private final String reasonCode;

    @JsonProperty("reason")
    private final String reason;
}
