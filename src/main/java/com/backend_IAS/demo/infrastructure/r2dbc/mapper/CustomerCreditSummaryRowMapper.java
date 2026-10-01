package com.backend_IAS.demo.infrastructure.r2dbc.mapper;

import com.backend_IAS.demo.domain.entity.CustomerCreditSummary;
import com.backend_IAS.demo.domain.enums.CustomerStatus;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;

public final class CustomerCreditSummaryRowMapper {
    private CustomerCreditSummaryRowMapper() {
    }

    public static CustomerCreditSummary toDomain(Row row) {
        return CustomerCreditSummary.builder()
                .customerId(row.get("customer_id", String.class))
                .status(CustomerStatus.valueOf(row.get("status", String.class)))
                .creditLimit(row.get("approval_limit", BigDecimal.class))
                .approvedAmount(row.get("approved_amount", BigDecimal.class))
                .availableAmount(row.get("available_amount", BigDecimal.class))
                .build();
    }
}
