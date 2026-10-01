package com.backend_IAS.demo.domain.port.portout;

import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.ApplicationPage;
import java.math.BigDecimal;
import reactor.core.publisher.Mono;

public interface ApplicationPort {
    Mono<CreditApplication> findByIdempotencyKey(String idempotencyKey);
    Mono<CreditApplication> findByReference(String applicationReference);
    Mono<BigDecimal> getTotalApproved(String customerId);
    Mono<CreditApplication> insert(CreditApplication application);
    Mono<ApplicationPage> findPage(int page, int size);
}
