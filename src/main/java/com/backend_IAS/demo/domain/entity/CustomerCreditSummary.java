package com.backend_IAS.demo.domain.entity;

import com.backend_IAS.demo.domain.enums.CustomerStatus;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class CustomerCreditSummary {
    private final String customerId;
    private final CustomerStatus status;
    private final BigDecimal creditLimit;
    private final BigDecimal approvedAmount;
    private final BigDecimal availableAmount;
}
