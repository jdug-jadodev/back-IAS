package com.backend_IAS.demo.application.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.backend_IAS.demo.application.validation.ApplicationValidator;
import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.port.portin.QueryApplicationsPort;
import com.backend_IAS.demo.domain.port.portout.ApplicationPort;
import com.backend_IAS.demo.exception.application.ApplicationNotFoundException;
import com.backend_IAS.demo.exception.application.InvalidApplicationDataException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class QueryApplicationsUseCaseTest {

    private final ApplicationPort applicationPort = mock(ApplicationPort.class);
    private final QueryApplicationsPort useCase =
            new QueryApplicationsUseCase(applicationPort, new ApplicationValidator());

    @Test
    void shouldQueryReferenceOnlyOnSubscriptionAndPreserveApplicationData() {
        CreditApplication application = application();
        when(applicationPort.findByReference("REF-001")).thenReturn(Mono.just(application));

        Mono<CreditApplication> result = useCase.findByReference("REF-001");

        verifyNoInteractions(applicationPort);
        StepVerifier.create(result)
                .expectNext(application)
                .verifyComplete();
        verify(applicationPort).findByReference("REF-001");
        verifyNoMoreInteractions(applicationPort);
    }

    @Test
    void shouldRejectMissingOrBlankReferenceWithoutQueryingPersistence() {
        for (String reference : new String[]{null, "", " \t\n"}) {
            StepVerifier.create(useCase.findByReference(reference))
                    .expectError(InvalidApplicationDataException.class)
                    .verify();
        }

        verifyNoInteractions(applicationPort);
    }

    @Test
    void shouldEmitApplicationNotFoundWhenReferenceDoesNotExist() {
        when(applicationPort.findByReference("REF-MISSING")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.findByReference("REF-MISSING"))
                .expectError(ApplicationNotFoundException.class)
                .verify();
    }

    @Test
    void shouldPropagateReferenceQueryFailureWithoutConvertingItToNotFound() {
        RuntimeException failure = new RuntimeException("Persistence unavailable");
        when(applicationPort.findByReference("REF-001")).thenReturn(Mono.error(failure));

        StepVerifier.create(useCase.findByReference("REF-001"))
                .expectErrorMatches(error -> error == failure)
                .verify();
        verify(applicationPort).findByReference("REF-001");
        verifyNoMoreInteractions(applicationPort);
    }

    @Test
    void shouldAcceptRecentLimitBoundariesAndQueryOnlyOnSubscription() {
        CreditApplication application = application();
        for (int limit : new int[]{1, 100}) {
            when(applicationPort.listRecent(limit)).thenReturn(Flux.just(application));
        }

        Flux<CreditApplication> minimumLimitResult = useCase.findRecent(1);
        Flux<CreditApplication> maximumLimitResult = useCase.findRecent(100);

        verifyNoInteractions(applicationPort);
        StepVerifier.create(minimumLimitResult).expectNext(application).verifyComplete();
        StepVerifier.create(maximumLimitResult).expectNext(application).verifyComplete();
        verify(applicationPort).listRecent(1);
        verify(applicationPort).listRecent(100);
        verifyNoMoreInteractions(applicationPort);
    }

    @Test
    void shouldRejectRecentLimitOutsideRangeWithoutQueryingPersistence() {
        for (int limit : new int[]{-1, 0, 101}) {
            StepVerifier.create(useCase.findRecent(limit))
                    .expectError(InvalidApplicationDataException.class)
                    .verify();
        }

        verifyNoInteractions(applicationPort);
    }

    @Test
    void shouldReturnEmptyListWhenThereAreNoApplications() {
        when(applicationPort.listRecent(20)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.findRecent(20)).verifyComplete();
    }

    @Test
    void shouldPropagateRecentQueryFailureWithoutReturningEmptyList() {
        RuntimeException failure = new RuntimeException("Persistence unavailable");
        when(applicationPort.listRecent(20)).thenReturn(Flux.error(failure));

        StepVerifier.create(useCase.findRecent(20))
                .expectErrorMatches(error -> error == failure)
                .verify();
    }

    private CreditApplication application() {
        return CreditApplication.builder()
                .data(ApplicationData.builder()
                        .applicationReference("REF-001")
                        .customerId("CLI-1001")
                        .amount(new BigDecimal("1000000.00"))
                        .termMonths(12)
                        .build())
                .decision(CreditDecision.builder()
                        .status(ApplicationStatus.APPROVED)
                        .build())
                .processedAt(Instant.parse("2026-10-01T12:00:00Z"))
                .build();
    }
}
