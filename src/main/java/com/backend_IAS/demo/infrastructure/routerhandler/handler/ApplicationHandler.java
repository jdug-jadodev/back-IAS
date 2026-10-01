package com.backend_IAS.demo.infrastructure.routerhandler.handler;

import com.backend_IAS.demo.application.dto.ApplicationRequestDto;
import com.backend_IAS.demo.application.mapper.ApplicationDtoMapper;
import com.backend_IAS.demo.domain.port.portin.ProcessApplicationPort;
import com.backend_IAS.demo.domain.port.portin.QueryApplicationsPort;
import com.backend_IAS.demo.exception.message.InfrastructureMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ApplicationHandler {

    private static final int DEFAULT_RECENT_LIMIT = 20;

    private final ProcessApplicationPort processApplicationPort;
    private final QueryApplicationsPort queryApplicationsPort;

    public Mono<ServerResponse> process(ServerRequest request) {
        return request.bodyToMono(ApplicationRequestDto.class)
                .onErrorMap(ServerWebInputException.class, error -> new ServerWebInputException(
                        InfrastructureMessages.INVALID_JSON_BODY, null, error))
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException(
                        InfrastructureMessages.REQUEST_BODY_REQUIRED)))
                .map(ApplicationDtoMapper::toDomain)
                .flatMap(processApplicationPort::process)
                .flatMap(result -> ServerResponse
                        .status(result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApplicationDtoMapper.toResponse(result)));
    }

    public Mono<ServerResponse> findByReference(ServerRequest request) {
        return queryApplicationsPort.findByReference(request.pathVariable("reference"))
                .map(ApplicationDtoMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> findRecent(ServerRequest request) {
        return Mono.defer(() -> {
            int limit = parseLimit(request);
            return queryApplicationsPort.findRecent(limit)
                    .map(ApplicationDtoMapper::toResponse)
                    .collectList()
                    .flatMap(response -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(response));
        });
    }

    private int parseLimit(ServerRequest request) {
        String limit = request.queryParam("limit").orElse(Integer.toString(DEFAULT_RECENT_LIMIT));
        try {
            return Integer.parseInt(limit);
        } catch (NumberFormatException error) {
            throw new ServerWebInputException(InfrastructureMessages.RECENT_LIMIT_MUST_BE_INTEGER, null, error);
        }
    }
}
