package com.backend_IAS.demo.application.mapper;

import com.backend_IAS.demo.application.dto.ProcessingResultDto;
import com.backend_IAS.demo.domain.entity.ProcessingResult;
import com.backend_IAS.demo.domain.factory.ProcessingResultFactory;

public final class ProcessingResultDtoMapper {

    private ProcessingResultDtoMapper() {
    }

    public static ProcessingResultDto toDto(ProcessingResult result) {
        return ProcessingResultDto.builder()
                .application(CreditApplicationDtoMapper.toDto(result.getApplication()))
                .created(result.isCreated())
                .build();
    }

    public static ProcessingResult toDomain(ProcessingResultDto result) {
        return result.isCreated()
                ? ProcessingResultFactory.created(CreditApplicationDtoMapper.toDomain(result.getApplication()))
                : ProcessingResultFactory.existing(CreditApplicationDtoMapper.toDomain(result.getApplication()));
    }
}
