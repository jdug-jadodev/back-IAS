package com.backend_IAS.demo.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("applicationReference")
    private String applicationReference;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("termMonths")
    private Integer termMonths;
}
