package com.backend_IAS.demo.application.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.backend_IAS.demo.application.validation.ApplicationValidator;
import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.ApplicationPage;
import com.backend_IAS.demo.domain.factory.ApplicationPageFactory;
import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.port.portin.QueryApplicationsPort;
import com.backend_IAS.demo.domain.port.portout.ApplicationPort;
import com.backend_IAS.demo.exception.application.ApplicationNotFoundException;
import com.backend_IAS.demo.exception.application.InvalidApplicationDataException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
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
    void shouldAcceptPageSizeBoundariesAndQueryOnlyOnSubscription() {
        CreditApplication application = application();
        for (int size : new int[]{1, 100}) {
            when(applicationPort.findPage(0, size)).thenReturn(
                    Mono.just(ApplicationPageFactory.create(List.of(application), 0, size, 1)));
        }

        Mono<ApplicationPage> minimumSizeResult = useCase.findPage(0, 1);
        Mono<ApplicationPage> maximumSizeResult = useCase.findPage(0, 100);

        verifyNoInteractions(applicationPort);
        StepVerifier.create(minimumSizeResult)
                .expectNext(ApplicationPageFactory.create(List.of(application), 0, 1, 1)).verifyComplete();
        StepVerifier.create(maximumSizeResult)
                .expectNext(ApplicationPageFactory.create(List.of(application), 0, 100, 1)).verifyComplete();
        verify(applicationPort).findPage(0, 1);
        verify(applicationPort).findPage(0, 100);
        verifyNoMoreInteractions(applicationPort);
    }

    @Test
    void shouldRejectInvalidPageAndSizeWithoutQueryingPersistence() {
        for (int[] pagination : new int[][]{{-1, 20}, {0, -1}, {0, 0}, {0, 101}}) {
            StepVerifier.create(useCase.findPage(pagination[0], pagination[1]))
                    .expectError(InvalidApplicationDataException.class)
                    .verify();
        }

        verifyNoInteractions(applicationPort);
    }

    @Test
    void shouldReturnAnEmptyPageWhenThereAreNoApplications() {
        ApplicationPage emptyPage = ApplicationPageFactory.create(List.of(), 0, 20, 0);
        when(applicationPort.findPage(0, 20)).thenReturn(Mono.just(emptyPage));

        StepVerifier.create(useCase.findPage(0, 20)).expectNext(emptyPage).verifyComplete();
    }

    @Test
    void shouldPropagatePageQueryFailureWithoutReturningAnEmptyPage() {
        RuntimeException failure = new RuntimeException("Persistence unavailable");
        when(applicationPort.findPage(0, 20)).thenReturn(Mono.error(failure));

        StepVerifier.create(useCase.findPage(0, 20))
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
