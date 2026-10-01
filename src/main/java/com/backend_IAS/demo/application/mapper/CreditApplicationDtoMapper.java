package com.backend_IAS.demo.application.mapper;

import com.backend_IAS.demo.application.dto.CreditApplicationDto;
import com.backend_IAS.demo.domain.entity.CreditApplication;

public final class CreditApplicationDtoMapper {

    private CreditApplicationDtoMapper() {
    }

    public static CreditApplicationDto toDto(CreditApplication application) {
        return CreditApplicationDto.builder()
                .data(ApplicationDataDtoMapper.toDto(application.getData()))
                .decision(CreditDecisionDtoMapper.toDto(application.getDecision()))
                .processedAt(application.getProcessedAt())
                .identifiedCustomerId(application.getIdentifiedCustomerId())
                .build();
    }

    public static CreditApplication toDomain(CreditApplicationDto application) {
        return CreditApplication.builder()
                .data(ApplicationDataDtoMapper.toDomain(application.getData()))
                .decision(CreditDecisionDtoMapper.toDomain(application.getDecision()))
                .processedAt(application.getProcessedAt())
                .identifiedCustomerId(application.getIdentifiedCustomerId())
                .build();
    }
}
