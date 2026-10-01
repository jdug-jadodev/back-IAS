package com.backend_IAS.demo.infrastructure.routerhandler.handler;

import com.backend_IAS.demo.application.dto.ApplicationRequestDto;
import com.backend_IAS.demo.application.mapper.ApplicationDtoMapper;
import com.backend_IAS.demo.application.mapper.ApplicationPageDtoMapper;
import com.backend_IAS.demo.exception.application.InvalidApplicationDataException;
import com.backend_IAS.demo.exception.message.ValidationMessages;
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

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ProcessApplicationPort processApplicationPort;
    private final QueryApplicationsPort queryApplicationsPort;

    public Mono<ServerResponse> process(ServerRequest request) {
        return request.bodyToMono(ApplicationRequestDto.class)
                .onErrorMap(ServerWebInputException.class, error -> new ServerWebInputException(
                        InfrastructureMessages.INVALID_JSON_BODY, null, error))
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException(
                        InfrastructureMessages.REQUEST_BODY_REQUIRED)))
                .map(body -> {
                    var keys = request.headers().header("Idempotency-Key");
                    if (keys.size() != 1) {
                        throw new InvalidApplicationDataException(ValidationMessages.IDEMPOTENCY_KEY_INVALID);
                    }
                    return ApplicationDtoMapper.toDomain(body, keys.getFirst());
                })
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

    public Mono<ServerResponse> findPage(ServerRequest request) {
        return Mono.defer(() -> {
            if (request.queryParam("limit").isPresent()) {
                return Mono.error(new InvalidApplicationDataException(ValidationMessages.LEGACY_LIMIT_NOT_SUPPORTED));
            }
            int page = parseInteger(request, "page", DEFAULT_PAGE);
            int size = parseInteger(request, "size", DEFAULT_PAGE_SIZE);
            return queryApplicationsPort.findPage(page, size)
                    .map(ApplicationPageDtoMapper::toResponse)
                    .flatMap(response -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(response));
        });
    }

    private int parseInteger(ServerRequest request, String parameter, int defaultValue) {
        String value = request.queryParam(parameter).orElse(Integer.toString(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new ServerWebInputException(
                    InfrastructureMessages.PAGINATION_PARAMETER_MUST_BE_INTEGER.formatted(parameter), null, error);
        }
    }
}
