package com.backend_IAS.demo.infrastructure.r2dbc.adapter.transaction;

import com.backend_IAS.demo.domain.port.portout.TransactionPort;
import com.backend_IAS.demo.infrastructure.r2dbc.mapper.PersistenceErrorMapper;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class R2dbcTransactionAdapter implements TransactionPort {

    private final TransactionalOperator transactionalOperator;

    @Override
    public <T> Mono<T> execute(Supplier<Mono<T>> operation) {
        return transactionalOperator.execute(status -> Mono.defer(operation))
                .singleOrEmpty()
                .onErrorMap(PersistenceErrorMapper::mapFailure);
    }
}
