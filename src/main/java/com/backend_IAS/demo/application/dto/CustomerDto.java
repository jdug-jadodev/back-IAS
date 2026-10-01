package com.backend_IAS.demo.application.dto;

import com.backend_IAS.demo.domain.enums.CustomerStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class CustomerDto {
    @JsonProperty("customerId")
    private final String customerId;

    @JsonProperty("status")
    private final CustomerStatus status;

    @JsonProperty("creditLimit")
    private final BigDecimal creditLimit;
}
