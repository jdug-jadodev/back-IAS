package com.backend_IAS.demo.application.mapper;

import com.backend_IAS.demo.application.dto.ApplicationDataDto;
import com.backend_IAS.demo.domain.entity.ApplicationData;

public final class ApplicationDataDtoMapper {

    private ApplicationDataDtoMapper() {
    }

    public static ApplicationDataDto toDto(ApplicationData data) {
        if (data == null) {
            return null;
        }
        return ApplicationDataDto.builder()
                .applicationReference(data.getApplicationReference())
                .idempotencyKey(data.getIdempotencyKey())
                .customerId(data.getCustomerId())
                .amount(data.getAmount())
                .termMonths(data.getTermMonths())
                .build();
    }

    public static ApplicationData toDomain(ApplicationDataDto data) {
        return ApplicationData.builder()
                .applicationReference(data.getApplicationReference())
                .idempotencyKey(data.getIdempotencyKey())
                .customerId(data.getCustomerId())
                .amount(data.getAmount())
                .termMonths(data.getTermMonths())
                .build();
    }
}
