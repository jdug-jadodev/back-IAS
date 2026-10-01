package com.backend_IAS.demo.application.usecase;

import com.backend_IAS.demo.application.mapper.CustomerCreditSummaryDtoMapper;
import com.backend_IAS.demo.application.validation.ApplicationValidator;
import com.backend_IAS.demo.domain.entity.CustomerCreditSummary;
import com.backend_IAS.demo.domain.port.portin.QueryCustomerCreditPort;
import com.backend_IAS.demo.domain.port.portout.CustomerCreditSummaryPort;
import com.backend_IAS.demo.exception.application.CustomerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public final class QueryCustomerCreditUseCase implements QueryCustomerCreditPort {
    private final CustomerCreditSummaryPort summaryPort;
    private final ApplicationValidator validator;

    @Override
    public Mono<CustomerCreditSummary> findSummary(String customerId) {
        return Mono.defer(() -> {
            validator.validateCustomerId(customerId);
            return summaryPort.findByCustomerId(customerId)
                    .map(CustomerCreditSummaryDtoMapper::toDto)
                    .switchIfEmpty(Mono.error(() -> new CustomerNotFoundException(customerId)));
        }).map(CustomerCreditSummaryDtoMapper::toDomain);
    }
}
