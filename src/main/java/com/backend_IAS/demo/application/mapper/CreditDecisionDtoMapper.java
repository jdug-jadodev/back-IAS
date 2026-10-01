package com.backend_IAS.demo.application.mapper;

import com.backend_IAS.demo.application.dto.CreditDecisionDto;
import com.backend_IAS.demo.domain.entity.CreditDecision;

public final class CreditDecisionDtoMapper {

    private CreditDecisionDtoMapper() {
    }

    public static CreditDecisionDto toDto(CreditDecision decision) {
        return CreditDecisionDto.builder()
                .status(decision.getStatus())
                .reason(decision.getReason())
                .reasonDescription(decision.getReasonDescription())
                .build();
    }

    public static CreditDecision toDomain(CreditDecisionDto decision) {
        return CreditDecision.builder()
                .status(decision.getStatus())
                .reason(decision.getReason())
                .reasonDescription(decision.getReasonDescription())
                .build();
    }
}
