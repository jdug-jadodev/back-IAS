package com.backend_IAS.demo.application.mapper;

import com.backend_IAS.demo.application.dto.ApplicationRequestDto;
import com.backend_IAS.demo.application.dto.ApplicationResponseDto;
import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.enums.RejectionReason;

public final class ApplicationDtoMapper {

    private ApplicationDtoMapper() {
    }

    public static ApplicationData toDomain(ApplicationRequestDto request) {
        return ApplicationData.builder()
                .applicationReference(request.getApplicationReference())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .termMonths(request.getTermMonths())
                .build();
    }

    public static ApplicationResponseDto toResponse(CreditApplication application) {
        ApplicationData data = application.getData();
        CreditDecision decision = application.getDecision();
        RejectionReason reason = decision.getReason();

        return ApplicationResponseDto.builder()
                .applicationReference(data.getApplicationReference())
                .customerId(data.getCustomerId())
                .amount(data.getAmount().toPlainString())
                .termMonths(data.getTermMonths())
                .status(decision.getStatus().name())
                .processedAt(application.getProcessedAt())
                .reasonCode(reason == null ? null : reason.getCode())
                .reason(reason == null ? null : reason.getDescription())
                .build();
    }
}
