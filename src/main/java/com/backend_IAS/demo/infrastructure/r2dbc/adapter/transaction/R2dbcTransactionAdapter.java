package com.backend_IAS.demo.infrastructure.r2dbc.adapter.transaction;

import com.backend_IAS.demo.domain.port.portout.TransactionPort;
import com.backend_IAS.demo.exception.database.PersistenceTimeoutException;
import com.backend_IAS.demo.exception.message.InfrastructureMessages;
import com.backend_IAS.demo.infrastructure.r2dbc.mapper.PersistenceErrorMapper;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
public class R2dbcTransactionAdapter implements TransactionPort {

    private final TransactionalOperator transactionalOperator;
    private final Duration operationTimeout;

    public R2dbcTransactionAdapter(TransactionalOperator transactionalOperator,
            @Value("${app.database.operation-timeout:10s}") Duration operationTimeout) {
        if (operationTimeout.isZero() || operationTimeout.isNegative()) {
            throw new IllegalArgumentException(InfrastructureMessages.OPERATION_TIMEOUT_INVALID);
        }
        this.transactionalOperator = transactionalOperator;
        this.operationTimeout = operationTimeout;
    }

    @Override
    public <T> Mono<T> execute(Supplier<Mono<T>> operation) {
        return transactionalOperator.execute(status -> Mono.defer(operation)
                        .timeout(operationTimeout, Mono.error(() -> new PersistenceTimeoutException(
                                new TimeoutException(InfrastructureMessages.OPERATION_TIMEOUT_REACHED)))))
                .singleOrEmpty()
                .onErrorMap(PersistenceErrorMapper::mapFailure);
    }
}
