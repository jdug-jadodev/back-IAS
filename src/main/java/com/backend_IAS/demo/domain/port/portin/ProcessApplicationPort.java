package com.backend_IAS.demo.domain.port.portin;

import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.ProcessingResult;
import reactor.core.publisher.Mono;

public interface ProcessApplicationPort {
    Mono<ProcessingResult> process(ApplicationData data);
}
