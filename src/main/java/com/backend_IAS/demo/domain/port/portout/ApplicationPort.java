package com.backend_IAS.demo.domain.port.portout;

import com.backend_IAS.demo.domain.entity.CreditApplication;
import java.math.BigDecimal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ApplicationPort {
    Mono<CreditApplication> findByReference(String applicationReference);
    Mono<BigDecimal> getTotalApproved(String customerId);
    Mono<CreditApplication> insert(CreditApplication application);
    Flux<CreditApplication> listRecent(int limit);
}
