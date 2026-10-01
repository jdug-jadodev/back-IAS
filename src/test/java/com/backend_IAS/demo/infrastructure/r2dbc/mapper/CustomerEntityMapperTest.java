package com.backend_IAS.demo.infrastructure.r2dbc.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.backend_IAS.demo.domain.entity.Customer;
import com.backend_IAS.demo.domain.enums.CustomerStatus;
import com.backend_IAS.demo.infrastructure.r2dbc.entity.CustomerEntity;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CustomerEntityMapperTest {

    @ParameterizedTest
    @EnumSource(CustomerStatus.class)
    void shouldPreservePersistedCustomerStatusAndCreditLimit(CustomerStatus status) {
        CustomerEntity entity = CustomerEntity.builder()
                .customerId("CLI-1001")
                .status(status.name())
                .creditLimit(new BigDecimal("10000000.123456789123456789"))
                .build();

        Customer customer = CustomerEntityMapper.toDomain(entity);

        assertEquals("CLI-1001", customer.getCustomerId());
        assertEquals(status, customer.getStatus());
        assertEquals(new BigDecimal("10000000.123456789123456789"), customer.getCreditLimit());
        assertEquals(entity, CustomerEntityMapper.toEntity(customer));
    }

    @Test
    void shouldNotTreatUnknownPersistedStatusAsAnEligibleCustomer() {
        CustomerEntity entity = CustomerEntity.builder()
                .customerId("CLI-1001")
                .status("UNKNOWN")
                .creditLimit(BigDecimal.TEN)
                .build();

        assertThrows(IllegalArgumentException.class, () -> CustomerEntityMapper.toDomain(entity));
    }
}
