package com.backend_IAS.demo.application.mapper;

import com.backend_IAS.demo.application.dto.CustomerCreditSummaryDto;
import com.backend_IAS.demo.application.dto.CustomerCreditSummaryResponseDto;
import com.backend_IAS.demo.domain.entity.CustomerCreditSummary;

public final class CustomerCreditSummaryDtoMapper {
    private CustomerCreditSummaryDtoMapper() {
    }

    public static CustomerCreditSummaryDto toDto(CustomerCreditSummary summary) {
        return CustomerCreditSummaryDto.builder()
                .customerId(summary.getCustomerId()).status(summary.getStatus())
                .creditLimit(summary.getCreditLimit()).approvedAmount(summary.getApprovedAmount())
                .availableAmount(summary.getAvailableAmount()).build();
    }

    public static CustomerCreditSummary toDomain(CustomerCreditSummaryDto summary) {
        return CustomerCreditSummary.builder()
                .customerId(summary.getCustomerId()).status(summary.getStatus())
                .creditLimit(summary.getCreditLimit()).approvedAmount(summary.getApprovedAmount())
                .availableAmount(summary.getAvailableAmount()).build();
    }

    public static CustomerCreditSummaryResponseDto toResponse(CustomerCreditSummary summary) {
        CustomerCreditSummaryDto dto = toDto(summary);
        return CustomerCreditSummaryResponseDto.builder()
                .customerId(dto.getCustomerId()).status(dto.getStatus().name())
                .creditLimit(dto.getCreditLimit().toPlainString())
                .approvedAmount(dto.getApprovedAmount().toPlainString())
                .availableAmount(dto.getAvailableAmount().toPlainString()).build();
    }
}
