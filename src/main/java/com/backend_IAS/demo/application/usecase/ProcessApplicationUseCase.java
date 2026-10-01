package com.backend_IAS.demo.application.usecase;

import com.backend_IAS.demo.application.dto.ApplicationDataDto;
import com.backend_IAS.demo.application.dto.CreditApplicationDto;
import com.backend_IAS.demo.application.dto.CreditDecisionDto;
import com.backend_IAS.demo.application.dto.ProcessingResultDto;
import com.backend_IAS.demo.application.mapper.ApplicationDataDtoMapper;
import com.backend_IAS.demo.application.mapper.CreditApplicationDtoMapper;
import com.backend_IAS.demo.application.mapper.CreditDecisionDtoMapper;
import com.backend_IAS.demo.application.mapper.CustomerDtoMapper;
import com.backend_IAS.demo.application.mapper.ProcessingResultDtoMapper;
import com.backend_IAS.demo.application.validation.ApplicationValidator;
import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.ProcessingResult;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import com.backend_IAS.demo.domain.factory.CreditDecisionFactory;
import com.backend_IAS.demo.domain.port.portin.ProcessApplicationPort;
import com.backend_IAS.demo.domain.port.portout.ApplicationPort;
import com.backend_IAS.demo.domain.port.portout.CustomerPort;
import com.backend_IAS.demo.domain.port.portout.TransactionPort;
import com.backend_IAS.demo.domain.rule.ApplicationMatchingRules;
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
        return Mono.defer(() -> processDto(ApplicationDataDtoMapper.toDto(data)))
                .map(ProcessingResultDtoMapper::toDomain);
    }

    private Mono<ProcessingResultDto> processDto(ApplicationDataDto data) {
        return Mono.defer(() -> {
            validator.validate(data);
            return applicationPort.findByReference(data.getApplicationReference())
                    .map(CreditApplicationDtoMapper::toDto)
                    .flatMap(original -> resolveExisting(data, original))
                    .switchIfEmpty(Mono.defer(() -> processNew(data)));
        });
    }

    private Mono<ProcessingResultDto> resolveExisting(ApplicationDataDto data, CreditApplicationDto original) {
        if (!ApplicationMatchingRules.matches(
                ApplicationDataDtoMapper.toDomain(original.getData()), ApplicationDataDtoMapper.toDomain(data))) {
            return Mono.error(new ReferenceConflictException(data.getApplicationReference()));
        }
        return Mono.just(ProcessingResultDto.builder()
                .application(original)
                .created(false)
                .build());
    }

    private Mono<ProcessingResultDto> processNew(ApplicationDataDto data) {
        return transactionPort.execute(() -> evaluateAndInsert(data))
                .onErrorResume(DuplicateReferenceException.class, error ->
                        applicationPort.findByReference(data.getApplicationReference())
                                .map(CreditApplicationDtoMapper::toDto)
                                .switchIfEmpty(Mono.error(() -> new PersistenceFailureException(error)))
                                .flatMap(original -> resolveExisting(data, original)));
    }

    private Mono<ProcessingResultDto> evaluateAndInsert(ApplicationDataDto data) {
        return customerPort.findWithLock(data.getCustomerId())
                .map(CustomerDtoMapper::toDto)
                .switchIfEmpty(Mono.error(() -> new CustomerNotFoundException(data.getCustomerId())))
                .flatMap(customer -> applicationPort.getTotalApproved(customer.getCustomerId())
                        .switchIfEmpty(Mono.error(() -> new PersistenceFailureException(
                                new IllegalStateException("Approved total query did not return a value"))))
                        .map(totalApproved -> CreditDecisionDtoMapper.toDto(approvalRules.evaluate(
                                ApplicationDataDtoMapper.toDomain(data),
                                CustomerDtoMapper.toDomain(customer), totalApproved)))
                        .flatMap(decision -> insert(data, customer.getCustomerId(), decision)))
                .onErrorResume(CustomerNotFoundException.class, error ->
                        insert(data, null, CreditDecisionDtoMapper.toDto(
                                CreditDecisionFactory.rejected(RejectionReason.CUSTOMER_NOT_FOUND))));
    }

    private Mono<ProcessingResultDto> insert(
            ApplicationDataDto data, String identifiedCustomerId, CreditDecisionDto decision) {
        CreditApplicationDto application = CreditApplicationDto.builder()
                .data(data)
                .decision(decision)
                .identifiedCustomerId(identifiedCustomerId)
                .build();

        return applicationPort.insert(CreditApplicationDtoMapper.toDomain(application))
                .map(CreditApplicationDtoMapper::toDto)
                .switchIfEmpty(Mono.error(() -> new PersistenceFailureException(
                        new IllegalStateException("Insertion did not return the persisted application"))))
                .map(persisted -> ProcessingResultDto.builder()
                        .application(persisted)
                        .created(true)
                        .build());
    }
}
