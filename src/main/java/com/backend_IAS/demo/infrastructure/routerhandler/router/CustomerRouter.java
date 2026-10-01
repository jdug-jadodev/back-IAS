package com.backend_IAS.demo.infrastructure.routerhandler.router;

import com.backend_IAS.demo.application.dto.CustomerCreditSummaryResponseDto;
import com.backend_IAS.demo.application.dto.ErrorResponseDto;
import com.backend_IAS.demo.infrastructure.routerhandler.handler.CustomerHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class CustomerRouter {
    @Bean
    @RouterOperation(path = "/customers/{customerId}/credit-summary", method = RequestMethod.GET,
            beanClass = CustomerHandler.class, beanMethod = "findCreditSummary",
            operation = @Operation(operationId = "findCustomerCreditSummary", tags = "Customers",
                    summary = "Consultar el resumen de cupo de un cliente",
                    description = "Solo las aprobaciones consumen cupo. El saldo es informativo y no reserva crédito. "
                            + "Un cliente bloqueado también puede consultarse.",
                    parameters = @Parameter(name = "customerId", in = ParameterIn.PATH, required = true,
                            description = "Identificador del cliente", example = "CLI-1001"),
                    responses = {
                            @ApiResponse(responseCode = "200", description = "Resumen del cliente",
                                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                            schema = @Schema(implementation = CustomerCreditSummaryResponseDto.class))),
                            @ApiResponse(responseCode = "400", description = "Identificador inválido",
                                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
                            @ApiResponse(responseCode = "404", description = "Cliente inexistente",
                                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
                            @ApiResponse(responseCode = "503", description = "Tiempo de espera de base de datos agotado",
                                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
                            @ApiResponse(responseCode = "500", description = "Fallo técnico",
                                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
                    }))
    public RouterFunction<ServerResponse> customerRoutes(CustomerHandler handler) {
        return RouterFunctions.route().GET("/customers/{customerId}/credit-summary", handler::findCreditSummary).build();
    }
}
