package com.backend_IAS.demo.domain.factory;

import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.ProcessingResult;
import java.util.Objects;

public final class ProcessingResultFactory {

    private ProcessingResultFactory() {
    }

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
