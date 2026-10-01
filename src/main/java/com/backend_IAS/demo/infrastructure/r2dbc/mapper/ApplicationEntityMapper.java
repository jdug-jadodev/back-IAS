package com.backend_IAS.demo.infrastructure.r2dbc.mapper;

import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import com.backend_IAS.demo.infrastructure.r2dbc.entity.ApplicationEntity;
import java.time.ZoneOffset;

public final class ApplicationEntityMapper {

    private ApplicationEntityMapper() {
    }

    public static CreditApplication toDomain(ApplicationEntity entity) {
        return CreditApplication.builder()
                .data(ApplicationData.builder()
                        .applicationReference(entity.getApplicationReference())
                        .idempotencyKey(entity.getIdempotencyKey())
                        .customerId(entity.getRequestedCustomerId())
                        .amount(entity.getAmount())
                        .termMonths(entity.getTermMonths())
                        .build())
                .decision(CreditDecision.builder()
                        .status(ApplicationStatus.valueOf(entity.getStatus()))
                        .reason(entity.getReasonCode() == null ? null : RejectionReason.valueOf(entity.getReasonCode()))
                        .reasonDescription(entity.getReason())
                        .build())
                .processedAt(entity.getProcessedAt().toInstant())
                .identifiedCustomerId(entity.getIdentifiedCustomerId())
                .build();
    }

    public static ApplicationEntity toEntity(CreditApplication application) {
        ApplicationData data = application.getData();
        CreditDecision decision = application.getDecision();
        RejectionReason reason = decision.getReason();

        return ApplicationEntity.builder()
                .applicationReference(data.getApplicationReference())
                .idempotencyKey(data.getIdempotencyKey())
                .requestedCustomerId(data.getCustomerId())
                .identifiedCustomerId(application.getIdentifiedCustomerId())
                .amount(data.getAmount())
                .termMonths(data.getTermMonths())
                .status(decision.getStatus().name())
                .reasonCode(reason == null ? null : reason.getCode())
                .reason(reason == null ? null : rejectionDescription(decision))
                .processedAt(application.getProcessedAt() == null
                        ? null : application.getProcessedAt().atOffset(ZoneOffset.UTC))
                .build();
    }

    private static String rejectionDescription(CreditDecision decision) {
        return decision.getReasonDescription() == null
                ? decision.getReason().getDescription()
                : decision.getReasonDescription();
    }
}
