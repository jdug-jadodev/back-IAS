package com.backend_IAS.demo.application.mapper;

import com.backend_IAS.demo.application.dto.CustomerDto;
import com.backend_IAS.demo.domain.entity.Customer;

public final class CustomerDtoMapper {

    private CustomerDtoMapper() {
    }

    public static CustomerDto toDto(Customer customer) {
        return CustomerDto.builder()
                .customerId(customer.getCustomerId())
                .status(customer.getStatus())
                .creditLimit(customer.getCreditLimit())
                .build();
    }

    public static Customer toDomain(CustomerDto customer) {
        return Customer.builder()
                .customerId(customer.getCustomerId())
                .status(customer.getStatus())
                .creditLimit(customer.getCreditLimit())
                .build();
    }
}
