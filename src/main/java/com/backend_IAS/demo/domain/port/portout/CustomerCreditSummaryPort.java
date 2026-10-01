package com.backend_IAS.demo.domain.port.portout;

import com.backend_IAS.demo.domain.entity.CustomerCreditSummary;
import reactor.core.publisher.Mono;

public interface CustomerCreditSummaryPort {
    Mono<CustomerCreditSummary> findByCustomerId(String customerId);
}
