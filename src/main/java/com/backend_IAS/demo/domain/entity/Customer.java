package com.backend_IAS.demo.domain.entity;

import java.math.BigDecimal;

import com.backend_IAS.demo.domain.enums.CustomerStatus;
import lombok.*;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class Customer {
    private final String customerId;
    private final CustomerStatus status;
    private final BigDecimal creditLimit;
}
