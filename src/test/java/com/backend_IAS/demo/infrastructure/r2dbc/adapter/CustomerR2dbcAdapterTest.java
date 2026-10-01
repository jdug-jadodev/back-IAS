package com.backend_IAS.demo.infrastructure.r2dbc.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.backend_IAS.demo.domain.entity.Customer;
import com.backend_IAS.demo.domain.enums.CustomerStatus;
import com.backend_IAS.demo.exception.database.PersistenceFailureException;
import com.backend_IAS.demo.infrastructure.r2dbc.entity.CustomerEntity;
import com.backend_IAS.demo.infrastructure.r2dbc.repository.CustomerR2dbcRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataAccessResourceFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CustomerR2dbcAdapterTest {

    private final CustomerR2dbcRepository repository = mock(CustomerR2dbcRepository.class);
    private final CustomerR2dbcAdapter adapter = new CustomerR2dbcAdapter(repository);

    @ParameterizedTest
    @EnumSource(CustomerStatus.class)
    void shouldRequestCustomerLockOnSubscriptionAndPreserveStatus(CustomerStatus status) {
        when(repository.findWithLock("CLI-1001")).thenReturn(Mono.just(CustomerEntity.builder()
                .customerId("CLI-1001")
                .status(status.name())
                .creditLimit(new BigDecimal("10000000.000000000000000001"))
                .build()));

        Mono<Customer> result = adapter.findWithLock("CLI-1001");

        verifyNoInteractions(repository);
        StepVerifier.create(result)
                .assertNext(customer -> {
                    assertEquals("CLI-1001", customer.getCustomerId());
                    assertEquals(status, customer.getStatus());
                    assertEquals(new BigDecimal("10000000.000000000000000001"), customer.getCreditLimit());
                })
                .verifyComplete();
        verify(repository).findWithLock("CLI-1001");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldReturnEmptyForUnknownCustomer() {
        when(repository.findWithLock("CLI-MISSING")).thenReturn(Mono.empty());

        StepVerifier.create(adapter.findWithLock("CLI-MISSING")).verifyComplete();
    }

    @Test
    void shouldTranslateDatabaseFailureInsteadOfReturningAnUnknownCustomer() {
        Throwable failure = new DataAccessResourceFailureException("Database unavailable");
        when(repository.findWithLock("CLI-1001")).thenReturn(Mono.error(failure));

        StepVerifier.create(adapter.findWithLock("CLI-1001"))
                .expectErrorSatisfies(error -> {
                    assertEquals(PersistenceFailureException.class, error.getClass());
                    assertSame(failure, error.getCause());
                })
                .verify();
    }

    @Test
    void shouldCaptureSynchronousRepositoryFailureOnSubscription() {
        Throwable failure = new DataAccessResourceFailureException("Repository invocation failed");
        when(repository.findWithLock("CLI-1001")).thenThrow(failure);

        Mono<Customer> result = adapter.findWithLock("CLI-1001");

        verifyNoInteractions(repository);
        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertEquals(PersistenceFailureException.class, error.getClass());
                    assertSame(failure, error.getCause());
                })
                .verify();
    }

    @Test
    void shouldNotHideInvalidPersistedCustomerStatus() {
        when(repository.findWithLock("CLI-1001")).thenReturn(Mono.just(CustomerEntity.builder()
                .customerId("CLI-1001")
                .status("UNKNOWN")
                .creditLimit(BigDecimal.TEN)
                .build()));

        StepVerifier.create(adapter.findWithLock("CLI-1001")).expectError(IllegalArgumentException.class).verify();
    }
}
