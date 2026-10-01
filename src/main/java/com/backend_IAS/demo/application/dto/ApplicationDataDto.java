package com.backend_IAS.demo.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class ApplicationDataDto {
    @JsonProperty("applicationReference")
    private final String applicationReference;

    @JsonProperty("customerId")
    private final String customerId;

    @JsonProperty("amount")
    private final BigDecimal amount;

    @JsonProperty("termMonths")
    private final Integer termMonths;
}
