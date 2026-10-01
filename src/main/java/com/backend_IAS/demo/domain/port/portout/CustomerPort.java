package com.backend_IAS.demo.domain.port.portout;

import com.backend_IAS.demo.domain.entity.Customer;
import reactor.core.publisher.Mono;

public interface CustomerPort {
    Mono<Customer> findWithLock(String customerId);
}
