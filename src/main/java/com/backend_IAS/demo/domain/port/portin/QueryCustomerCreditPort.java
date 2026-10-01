package com.backend_IAS.demo.domain.port.portin;

import com.backend_IAS.demo.domain.entity.CustomerCreditSummary;
import reactor.core.publisher.Mono;

public interface QueryCustomerCreditPort {
    Mono<CustomerCreditSummary> findSummary(String customerId);
}
