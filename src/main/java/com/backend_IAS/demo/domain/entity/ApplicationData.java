package com.backend_IAS.demo.domain.entity;

import java.math.BigDecimal;
import java.util.Objects;
import lombok.*;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class ApplicationData {
    private final String applicationReference;
    private final String customerId;
    private final BigDecimal amount;
    private final Integer termMonths;

    public boolean matches(ApplicationData other) {
        return other != null
                && Objects.equals(applicationReference, other.applicationReference)
                && Objects.equals(customerId, other.customerId)
                && amount != null
                && other.amount != null
                && amount.compareTo(other.amount) == 0
                && Objects.equals(termMonths, other.termMonths);
    }
}
