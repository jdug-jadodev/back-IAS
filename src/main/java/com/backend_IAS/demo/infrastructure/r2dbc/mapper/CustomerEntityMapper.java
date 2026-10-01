package com.backend_IAS.demo.infrastructure.r2dbc.mapper;

import com.backend_IAS.demo.domain.entity.Customer;
import com.backend_IAS.demo.domain.enums.CustomerStatus;
import com.backend_IAS.demo.infrastructure.r2dbc.entity.CustomerEntity;

public final class CustomerEntityMapper {

    private CustomerEntityMapper() {
    }

    public static Customer toDomain(CustomerEntity entity) {
        return Customer.builder()
                .customerId(entity.getCustomerId())
                .status(CustomerStatus.valueOf(entity.getStatus()))
                .creditLimit(entity.getCreditLimit())
                .build();
    }

    public static CustomerEntity toEntity(Customer customer) {
        return CustomerEntity.builder()
                .customerId(customer.getCustomerId())
                .status(customer.getStatus().name())
                .creditLimit(customer.getCreditLimit())
                .build();
    }
}
