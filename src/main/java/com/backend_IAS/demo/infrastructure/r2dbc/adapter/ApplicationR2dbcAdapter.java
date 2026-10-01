package com.backend_IAS.demo.infrastructure.r2dbc.adapter;

import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.ApplicationPage;
import com.backend_IAS.demo.domain.port.portout.ApplicationPort;
import com.backend_IAS.demo.infrastructure.r2dbc.mapper.ApplicationEntityMapper;
import com.backend_IAS.demo.infrastructure.r2dbc.mapper.ApplicationPageEntityMapper;
import com.backend_IAS.demo.infrastructure.r2dbc.mapper.PersistenceErrorMapper;
import com.backend_IAS.demo.infrastructure.r2dbc.repository.ApplicationR2dbcRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class ApplicationR2dbcAdapter implements ApplicationPort {

    private final ApplicationR2dbcRepository repository;

    @Override
    public Mono<CreditApplication> findByIdempotencyKey(String idempotencyKey) {
        return Mono.defer(() -> repository.findByIdempotencyKey(idempotencyKey))
                .map(ApplicationEntityMapper::toDomain)
                .onErrorMap(PersistenceErrorMapper::mapFailure);
    }

    @Override
    public Mono<CreditApplication> findByReference(String applicationReference) {
        return Mono.defer(() -> repository.findByApplicationReference(applicationReference))
                .map(ApplicationEntityMapper::toDomain)
                .onErrorMap(PersistenceErrorMapper::mapFailure);
    }

    @Override
    public Mono<BigDecimal> getTotalApproved(String customerId) {
        return Mono.defer(() -> repository.getTotalApproved(customerId))
                .onErrorMap(PersistenceErrorMapper::mapFailure);
    }

    @Override
    public Mono<CreditApplication> insert(CreditApplication application) {
        return Mono.defer(() -> repository.insert(ApplicationEntityMapper.toEntity(application)))
                .map(ApplicationEntityMapper::toDomain)
                .onErrorMap(PersistenceErrorMapper::mapInsertFailure);
    }

    @Override
    public Mono<ApplicationPage> findPage(int page, int size) {
        return Flux.defer(() -> repository.findPage(size, (long) page * size))
                .collectList()
                .map(rows -> ApplicationPageEntityMapper.toDomain(rows, page, size))
                .onErrorMap(PersistenceErrorMapper::mapFailure);
    }
}
