package com.backend_IAS.demo.application.usecase;

import com.backend_IAS.demo.application.mapper.CreditApplicationDtoMapper;
import com.backend_IAS.demo.application.mapper.ApplicationPageDtoMapper;
import com.backend_IAS.demo.application.validation.ApplicationValidator;
import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.ApplicationPage;
import com.backend_IAS.demo.domain.port.portin.QueryApplicationsPort;
import com.backend_IAS.demo.domain.port.portout.ApplicationPort;
import com.backend_IAS.demo.exception.application.ApplicationNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public final class QueryApplicationsUseCase implements QueryApplicationsPort {

    private final ApplicationPort applicationPort;
    private final ApplicationValidator validator;

    @Override
    public Mono<CreditApplication> findByReference(String applicationReference) {
        return Mono.defer(() -> {
            validator.validateReference(applicationReference);
            return applicationPort.findByReference(applicationReference)
                    .map(CreditApplicationDtoMapper::toDto)
                    .switchIfEmpty(Mono.error(() -> new ApplicationNotFoundException(applicationReference)));
        }).map(CreditApplicationDtoMapper::toDomain);
    }

    @Override
    public Mono<ApplicationPage> findPage(int page, int size) {
        return Mono.defer(() -> {
            validator.validatePagination(page, size);
            return applicationPort.findPage(page, size)
                    .map(ApplicationPageDtoMapper::toDto);
        }).map(ApplicationPageDtoMapper::toDomain);
    }
}
