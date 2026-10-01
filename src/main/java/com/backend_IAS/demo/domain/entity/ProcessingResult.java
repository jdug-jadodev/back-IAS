package com.backend_IAS.demo.domain.entity;

import java.util.Objects;
import lombok.*;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class ProcessingResult {
    private final CreditApplication application;
    private final boolean created;

    public static ProcessingResult created(CreditApplication application) {
        return ProcessingResult.builder()
                .application(Objects.requireNonNull(application, "application is required"))
                .created(true)
                .build();
    }

    public static ProcessingResult existing(CreditApplication application) {
        return ProcessingResult.builder()
                .application(Objects.requireNonNull(application, "application is required"))
                .created(false)
                .build();
    }
}
