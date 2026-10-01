package com.backend_IAS.demo.domain.port.portin;

import com.backend_IAS.demo.domain.entity.CreditApplication;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface QueryApplicationsPort {
    Mono<CreditApplication> findByReference(String applicationReference);
    Flux<CreditApplication> findRecent(int limit);
}
