package com.backend_IAS.demo.infrastructure.r2dbc.adapter;

import com.backend_IAS.demo.domain.entity.Customer;
import com.backend_IAS.demo.domain.port.portout.CustomerPort;
import com.backend_IAS.demo.infrastructure.r2dbc.mapper.CustomerEntityMapper;
import com.backend_IAS.demo.infrastructure.r2dbc.mapper.PersistenceErrorMapper;
import com.backend_IAS.demo.infrastructure.r2dbc.repository.CustomerR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public final class CustomerR2dbcAdapter implements CustomerPort {

    private final CustomerR2dbcRepository repository;

    @Override
    public Mono<Customer> findWithLock(String customerId) {
        return Mono.defer(() -> repository.findWithLock(customerId))
                .map(CustomerEntityMapper::toDomain)
                .onErrorMap(PersistenceErrorMapper::mapFailure);
    }
}
