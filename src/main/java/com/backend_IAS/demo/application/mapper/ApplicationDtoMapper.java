package com.backend_IAS.demo.application.mapper;

import com.backend_IAS.demo.application.dto.ApplicationDataDto;
import com.backend_IAS.demo.application.dto.ApplicationRequestDto;
import com.backend_IAS.demo.application.dto.ApplicationResponseDto;
import com.backend_IAS.demo.application.dto.CreditApplicationDto;
import com.backend_IAS.demo.application.dto.CreditDecisionDto;
import com.backend_IAS.demo.application.dto.ProcessingResultDto;
import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.ProcessingResult;
import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import com.backend_IAS.demo.exception.message.ApplicationMessages;

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
        return toResponse(CreditApplicationDtoMapper.toDto(application));
    }

    private static ApplicationResponseDto toResponse(CreditApplicationDto application) {
        ApplicationDataDto data = application.getData();
        CreditDecisionDto decision = application.getDecision();
        RejectionReason reason = decision.getReason();

        return ApplicationResponseDto.builder()
                .applicationReference(data.getApplicationReference())
                .customerId(data.getCustomerId())
                .amount(data.getAmount().toPlainString())
                .termMonths(data.getTermMonths())
                .status(decision.getStatus().name())
                .message(decisionMessage(decision, false))
                .processedAt(application.getProcessedAt())
                .reasonCode(reason == null ? null : reason.getCode())
                .reason(reason == null ? null : rejectionDescription(decision))
                .build();
    }

    public static ApplicationResponseDto toResponse(ProcessingResult result) {
        ProcessingResultDto dto = ProcessingResultDtoMapper.toDto(result);
        return toResponse(dto.getApplication()).toBuilder()
                .message(decisionMessage(dto.getApplication().getDecision(), !dto.isCreated()))
                .build();
    }

    private static String decisionMessage(CreditDecisionDto decision, boolean existing) {
        if (decision.getStatus() == ApplicationStatus.APPROVED) {
            return existing ? ApplicationMessages.APPLICATION_ALREADY_APPROVED : ApplicationMessages.APPLICATION_APPROVED;
        }
        return existing ? ApplicationMessages.APPLICATION_ALREADY_REJECTED : ApplicationMessages.APPLICATION_REJECTED;
    }

    private static String rejectionDescription(CreditDecisionDto decision) {
        return decision.getReasonDescription() == null
                ? decision.getReason().getDescription()
                : decision.getReasonDescription();
    }
}
