package com.backend_IAS.demo.application.usecase;

import com.backend_IAS.demo.application.validation.ApplicationValidator;
import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.entity.ProcessingResult;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import com.backend_IAS.demo.domain.port.portin.ProcessApplicationPort;
import com.backend_IAS.demo.domain.port.portout.ApplicationPort;
import com.backend_IAS.demo.domain.port.portout.CustomerPort;
import com.backend_IAS.demo.domain.port.portout.TransactionPort;
import com.backend_IAS.demo.domain.rule.ApprovalRules;
import com.backend_IAS.demo.exception.application.CustomerNotFoundException;
import com.backend_IAS.demo.exception.application.ReferenceConflictException;
import com.backend_IAS.demo.exception.database.DuplicateReferenceException;
import com.backend_IAS.demo.exception.database.PersistenceFailureException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public final class ProcessApplicationUseCase implements ProcessApplicationPort {

    private final CustomerPort customerPort;
    private final ApplicationPort applicationPort;
    private final TransactionPort transactionPort;
    private final ApplicationValidator validator;
    private final ApprovalRules approvalRules;

    @Override
    public Mono<ProcessingResult> process(ApplicationData data) {
        return Mono.defer(() -> {
            validator.validate(data);
            return applicationPort.findByReference(data.getApplicationReference())
                    .flatMap(original -> resolveExisting(data, original))
                    .switchIfEmpty(Mono.defer(() -> processNew(data)));
        });
    }

    private Mono<ProcessingResult> resolveExisting(ApplicationData data, CreditApplication original) {
        if (!original.getData().matches(data)) {
            return Mono.error(new ReferenceConflictException(data.getApplicationReference()));
        }
        return Mono.just(ProcessingResult.existing(original));
    }

    private Mono<ProcessingResult> processNew(ApplicationData data) {
        return transactionPort.execute(() -> evaluateAndInsert(data))
                .onErrorResume(DuplicateReferenceException.class, error ->
                        applicationPort.findByReference(data.getApplicationReference())
                                .switchIfEmpty(Mono.error(() -> new PersistenceFailureException(error)))
                                .flatMap(original -> resolveExisting(data, original)));
    }

    private Mono<ProcessingResult> evaluateAndInsert(ApplicationData data) {
        return customerPort.findWithLock(data.getCustomerId())
                .switchIfEmpty(Mono.error(() -> new CustomerNotFoundException(data.getCustomerId())))
                .flatMap(customer -> applicationPort.getTotalApproved(customer.getCustomerId())
                        .switchIfEmpty(Mono.error(() -> new PersistenceFailureException(
                                new IllegalStateException("Approved total query did not return a value"))))
                        .map(totalApproved -> approvalRules.evaluate(data, customer, totalApproved))
                        .flatMap(decision -> insert(data, customer.getCustomerId(), decision)))
                .onErrorResume(CustomerNotFoundException.class, error ->
                        insert(data, null, CreditDecision.rejected(RejectionReason.CUSTOMER_NOT_FOUND)));
    }

    private Mono<ProcessingResult> insert(ApplicationData data, String identifiedCustomerId, CreditDecision decision) {
        CreditApplication application = CreditApplication.builder()
                .data(data)
                .decision(decision)
                .identifiedCustomerId(identifiedCustomerId)
                .build();

        return applicationPort.insert(application)
                .switchIfEmpty(Mono.error(() -> new PersistenceFailureException(
                        new IllegalStateException("Insertion did not return the persisted application"))))
                .map(ProcessingResult::created);
    }
}
