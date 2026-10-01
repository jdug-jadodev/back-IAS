package com.backend_IAS.demo.domain.entity;

import lombok.*;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class ProcessingResult {
    private final CreditApplication application;
    private final boolean created;
}
