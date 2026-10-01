package com.backend_IAS.demo.application.mapper;

import com.backend_IAS.demo.application.dto.ApplicationPageDto;
import com.backend_IAS.demo.application.dto.ApplicationPageResponseDto;
import com.backend_IAS.demo.domain.entity.ApplicationPage;

public final class ApplicationPageDtoMapper {
    private ApplicationPageDtoMapper() {
    }

    public static ApplicationPageDto toDto(ApplicationPage page) {
        return ApplicationPageDto.builder()
                .content(page.getContent().stream().map(CreditApplicationDtoMapper::toDto).toList())
                .page(page.getPage())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    public static ApplicationPage toDomain(ApplicationPageDto page) {
        return ApplicationPage.builder()
                .content(page.getContent().stream().map(CreditApplicationDtoMapper::toDomain).toList())
                .page(page.getPage())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    public static ApplicationPageResponseDto toResponse(ApplicationPage page) {
        ApplicationPageDto dto = toDto(page);
        return ApplicationPageResponseDto.builder()
                .content(dto.getContent().stream().map(ApplicationDtoMapper::toResponse).toList())
                .page(dto.getPage())
                .size(dto.getSize())
                .totalElements(dto.getTotalElements())
                .totalPages(dto.getTotalPages())
                .first(dto.isFirst())
                .last(dto.isLast())
                .build();
    }
}
