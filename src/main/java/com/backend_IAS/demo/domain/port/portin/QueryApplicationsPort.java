package com.backend_IAS.demo.domain.port.portin;

import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.ApplicationPage;
import reactor.core.publisher.Mono;

public interface QueryApplicationsPort {
    Mono<CreditApplication> findByReference(String applicationReference);
    Mono<ApplicationPage> findPage(int page, int size);
}
