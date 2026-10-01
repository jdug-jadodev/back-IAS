package com.backend_IAS.demo.infrastructure.r2dbc.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import com.backend_IAS.demo.exception.database.DuplicateReferenceException;
import com.backend_IAS.demo.exception.database.PersistenceFailureException;
import com.backend_IAS.demo.infrastructure.r2dbc.entity.ApplicationEntity;
import com.backend_IAS.demo.infrastructure.r2dbc.repository.ApplicationR2dbcRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Publisher;
import org.springframework.dao.DataAccessResourceFailureException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApplicationR2dbcAdapterTest {

    private final ApplicationR2dbcRepository repository = mock(ApplicationR2dbcRepository.class);
    private final ApplicationR2dbcAdapter adapter = new ApplicationR2dbcAdapter(repository);

    @Test
    void shouldQueryByReferenceOnlyOnSubscription() {
        when(repository.findByApplicationReference("REF-001")).thenReturn(Mono.just(entity("REF-001")));

        Mono<CreditApplication> result = adapter.findByReference("REF-001");

        verifyNoInteractions(repository);
        StepVerifier.create(result)
                .assertNext(application -> {
                    assertEquals("REF-001", application.getData().getApplicationReference());
                    assertEquals(ApplicationStatus.APPROVED, application.getDecision().getStatus());
                    assertEquals(Instant.parse("2026-10-01T12:00:00Z"), application.getProcessedAt());
                })
                .verifyComplete();
        verify(repository).findByApplicationReference("REF-001");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldPreserveEmptyReferenceLookupWithoutInventingAnApplication() {
        when(repository.findByApplicationReference("REF-MISSING")).thenReturn(Mono.empty());

        StepVerifier.create(adapter.findByReference("REF-MISSING")).verifyComplete();

        verify(repository).findByApplicationReference("REF-MISSING");
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1000002.123456789123456790"})
    void shouldReturnApprovedTotalWithoutRoundingOrAdditionalQueries(String value) {
        BigDecimal total = new BigDecimal(value);
        when(repository.getTotalApproved("CLI-1001")).thenReturn(Mono.just(total));

        Mono<BigDecimal> result = adapter.getTotalApproved("CLI-1001");

        verifyNoInteractions(repository);
        StepVerifier.create(result).expectNext(total).verifyComplete();
        verify(repository).getTotalApproved("CLI-1001");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldNotHideAnUnexpectedEmptyTotalQueryByReturningZero() {
        when(repository.getTotalApproved("CLI-1001")).thenReturn(Mono.empty());

        StepVerifier.create(adapter.getTotalApproved("CLI-1001")).verifyComplete();
    }

    @Test
    void shouldInsertOnSubscriptionAndReturnDatabaseGeneratedTimestamp() {
        CreditApplication requested = application();
        when(repository.insert(any(ApplicationEntity.class))).thenReturn(Mono.just(entity("REF-001")));

        Mono<CreditApplication> result = adapter.insert(requested);

        verifyNoInteractions(repository);
        StepVerifier.create(result)
                .assertNext(saved -> {
                    assertEquals(requested.getData(), saved.getData());
                    assertEquals(Instant.parse("2026-10-01T12:00:00Z"), saved.getProcessedAt());
                })
                .verifyComplete();
        ArgumentCaptor<ApplicationEntity> inserted = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(repository).insert(inserted.capture());
        assertNull(inserted.getValue().getId());
        assertEquals(requested.getProcessedAt().atOffset(ZoneOffset.UTC), inserted.getValue().getProcessedAt());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldPersistUnknownCustomerRejectionWithoutCreatingACustomerLink() {
        CreditApplication requested = application().toBuilder()
                .data(application().getData().toBuilder().customerId("CLI-MISSING").build())
                .decision(CreditDecision.builder()
                        .status(ApplicationStatus.REJECTED)
                        .reason(RejectionReason.CUSTOMER_NOT_FOUND)
                        .reasonDescription("El cliente no estaba registrado al procesar la solicitud")
                        .build())
                .identifiedCustomerId(null)
                .build();
        ApplicationEntity persisted = entity("REF-001").toBuilder()
                .requestedCustomerId("CLI-MISSING")
                .identifiedCustomerId(null)
                .status("REJECTED")
                .reasonCode("CUSTOMER_NOT_FOUND")
                .reason(requested.getDecision().getReasonDescription())
                .build();
        when(repository.insert(any(ApplicationEntity.class))).thenReturn(Mono.just(persisted));

        StepVerifier.create(adapter.insert(requested))
                .assertNext(saved -> {
                    assertEquals("CLI-MISSING", saved.getData().getCustomerId());
                    assertNull(saved.getIdentifiedCustomerId());
                    assertEquals(requested.getDecision(), saved.getDecision());
                })
                .verifyComplete();
        ArgumentCaptor<ApplicationEntity> inserted = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(repository).insert(inserted.capture());
        assertEquals("CLI-MISSING", inserted.getValue().getRequestedCustomerId());
        assertNull(inserted.getValue().getIdentifiedCustomerId());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldPassRecentLimitAndPreserveRepositoryOrder() {
        when(repository.listRecent(2)).thenReturn(Flux.just(entity("REF-003"), entity("REF-002")));

        Flux<CreditApplication> result = adapter.listRecent(2);

        verifyNoInteractions(repository);
        StepVerifier.create(result)
                .expectNextMatches(application -> application.getData().getApplicationReference().equals("REF-003"))
                .expectNextMatches(application -> application.getData().getApplicationReference().equals("REF-002"))
                .verifyComplete();
        verify(repository).listRecent(2);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldPreserveEmptyRecentList() {
        when(repository.listRecent(20)).thenReturn(Flux.empty());

        StepVerifier.create(adapter.listRecent(20)).verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"reference", "total", "insert", "recent"})
    void shouldTranslateReactiveDatabaseFailuresAndPreserveTheirCause(String operation) {
        Throwable failure = new DataAccessResourceFailureException("Database unavailable");

        StepVerifier.create(failingOperation(operation, failure, false))
                .expectErrorSatisfies(error -> {
                    assertEquals(PersistenceFailureException.class, error.getClass());
                    assertSame(failure, error.getCause());
                })
                .verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"reference", "total", "insert", "recent"})
    void shouldCaptureSynchronousRepositoryFailuresInTheReactivePipeline(String operation) {
        Throwable failure = new DataAccessResourceFailureException("Repository invocation failed");

        Publisher<?> result = failingOperation(operation, failure, true);

        verifyNoInteractions(repository);
        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertEquals(PersistenceFailureException.class, error.getClass());
                    assertSame(failure, error.getCause());
                })
                .verify();
    }

    @Test
    void shouldPropagateDuplicateReferenceWithoutRetryingOrOverwriting() {
        DuplicateReferenceException failure = new DuplicateReferenceException(new IllegalStateException("Duplicate"));
        when(repository.insert(any(ApplicationEntity.class))).thenReturn(Mono.error(failure));

        StepVerifier.create(adapter.insert(application())).expectErrorMatches(error -> error == failure).verify();

        verify(repository).insert(any(ApplicationEntity.class));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldNotReclassifyMappingErrorsAsPersistenceFailures() {
        ApplicationEntity invalid = entity("REF-001").toBuilder().status("UNKNOWN").build();
        when(repository.findByApplicationReference("REF-001")).thenReturn(Mono.just(invalid));

        StepVerifier.create(adapter.findByReference("REF-001")).expectError(IllegalArgumentException.class).verify();
    }

    private Publisher<?> failingOperation(String operation, Throwable failure, boolean synchronous) {
        return switch (operation) {
            case "reference" -> {
                if (synchronous) {
                    when(repository.findByApplicationReference("REF-001")).thenThrow(failure);
                } else {
                    when(repository.findByApplicationReference("REF-001")).thenReturn(Mono.error(failure));
                }
                yield adapter.findByReference("REF-001");
            }
            case "total" -> {
                if (synchronous) {
                    when(repository.getTotalApproved("CLI-1001")).thenThrow(failure);
                } else {
                    when(repository.getTotalApproved("CLI-1001")).thenReturn(Mono.error(failure));
                }
                yield adapter.getTotalApproved("CLI-1001");
            }
            case "insert" -> {
                if (synchronous) {
                    when(repository.insert(any(ApplicationEntity.class))).thenThrow(failure);
                } else {
                    when(repository.insert(any(ApplicationEntity.class))).thenReturn(Mono.error(failure));
                }
                yield adapter.insert(application());
            }
            case "recent" -> {
                if (synchronous) {
                    when(repository.listRecent(20)).thenThrow(failure);
                } else {
                    when(repository.listRecent(20)).thenReturn(Flux.error(failure));
                }
                yield adapter.listRecent(20);
            }
            default -> throw new IllegalArgumentException("Unknown test operation: " + operation);
        };
    }

    private ApplicationEntity entity(String reference) {
        return ApplicationEntity.builder()
                .id(42L)
                .applicationReference(reference)
                .requestedCustomerId("CLI-1001")
                .identifiedCustomerId("CLI-1001")
                .amount(new BigDecimal("1000000.123456789123456789"))
                .termMonths(12)
                .status("APPROVED")
                .processedAt(OffsetDateTime.parse("2026-10-01T12:00:00Z"))
                .build();
    }

    private CreditApplication application() {
        return CreditApplication.builder()
                .data(ApplicationData.builder()
                        .applicationReference("REF-001")
                        .customerId("CLI-1001")
                        .amount(new BigDecimal("1000000.123456789123456789"))
                        .termMonths(12)
                        .build())
                .decision(CreditDecision.builder().status(ApplicationStatus.APPROVED).build())
                .identifiedCustomerId("CLI-1001")
                .processedAt(Instant.parse("2000-01-01T00:00:00Z"))
                .build();
    }
}
