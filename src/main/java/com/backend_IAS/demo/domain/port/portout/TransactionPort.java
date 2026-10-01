package com.backend_IAS.demo.domain.port.portout;

import java.util.function.Supplier;
import reactor.core.publisher.Mono;

public interface TransactionPort {
    <T> Mono<T> execute(Supplier<Mono<T>> operation);
}
