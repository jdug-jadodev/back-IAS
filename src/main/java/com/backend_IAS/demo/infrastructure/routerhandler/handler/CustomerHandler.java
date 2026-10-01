package com.backend_IAS.demo.infrastructure.routerhandler.handler;

import com.backend_IAS.demo.application.mapper.CustomerCreditSummaryDtoMapper;
import com.backend_IAS.demo.domain.port.portin.QueryCustomerCreditPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CustomerHandler {
    private final QueryCustomerCreditPort queryCustomerCreditPort;

    public Mono<ServerResponse> findCreditSummary(ServerRequest request) {
        return queryCustomerCreditPort.findSummary(request.pathVariable("customerId"))
                .map(CustomerCreditSummaryDtoMapper::toResponse)
                .flatMap(response -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(response));
    }
}
