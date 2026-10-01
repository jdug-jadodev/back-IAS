package com.backend_IAS.demo.domain.entity;

import java.time.Instant;
import lombok.*;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class CreditApplication {
    private final ApplicationData data;
    private final CreditDecision decision;
    private final Instant processedAt;
    private final String identifiedCustomerId;
}
