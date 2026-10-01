package com.backend_IAS.demo.domain.entity;

import java.math.BigDecimal;
import lombok.*;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class ApplicationData {
    private final String applicationReference;
    private final String customerId;
    private final BigDecimal amount;
    private final Integer termMonths;
}
