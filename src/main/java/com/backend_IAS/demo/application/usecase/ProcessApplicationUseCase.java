package com.backend_IAS.demo.application.usecase;

import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.ProcessingResult;
import com.backend_IAS.demo.domain.port.portin.ProcessApplicationPort;
import com.backend_IAS.demo.domain.port.portout.ApplicationPort;
import com.backend_IAS.demo.domain.port.portout.CustomerPort;
import com.backend_IAS.demo.domain.port.portout.TransactionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public final class ProcessApplicationUseCase implements ProcessApplicationPort {

    private final CustomerPort customerPort;
    private final ApplicationPort applicationPort;
    private final TransactionPort transactionPort;

    @Override
    public Mono<ProcessingResult> process(ApplicationData data) {
        return Mono.error(() -> new UnsupportedOperationException(
                "Credit application processing is not implemented yet"));
    }
}
