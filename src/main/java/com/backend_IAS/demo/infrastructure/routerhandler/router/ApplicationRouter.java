package com.backend_IAS.demo.infrastructure.routerhandler.router;

import com.backend_IAS.demo.application.dto.ApplicationRequestDto;
import com.backend_IAS.demo.application.dto.ApplicationResponseDto;
import com.backend_IAS.demo.application.dto.ErrorResponseDto;
import com.backend_IAS.demo.infrastructure.routerhandler.handler.ApplicationHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class ApplicationRouter {

    @Bean
    @RouterOperations({
            @RouterOperation(
                    path = "/applications",
                    method = RequestMethod.POST,
                    beanClass = ApplicationHandler.class,
                    beanMethod = "process",
                    operation = @Operation(
                            operationId = "processApplication",
                            tags = "Applications",
                            summary = "Procesar una solicitud de crédito",
                            description = "Guarda una nueva solicitud aprobada o rechazada. "
                                    + "Un reintento con la misma referencia y datos devuelve el resultado original "
                                    + "sin consumir cupo adicional. El monto se compara numéricamente.",
                            requestBody = @RequestBody(
                                    required = true,
                                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                            schema = @Schema(implementation = ApplicationRequestDto.class))),
                            responses = {
                                    @ApiResponse(responseCode = "201", description = "Nueva solicitud aprobada o rechazada",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ApplicationResponseDto.class))),
                                    @ApiResponse(responseCode = "200", description = "Solicitud ya aprobada o rechazada; resultado original",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ApplicationResponseDto.class))),
                                    @ApiResponse(responseCode = "400", description = "Datos obligatorios ausentes, identificadores en blanco o JSON inválido",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponseDto.class))),
                                    @ApiResponse(responseCode = "409", description = "La referencia ya existe con datos diferentes",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponseDto.class))),
                                    @ApiResponse(responseCode = "413", description = "El cuerpo supera el tamaño permitido",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponseDto.class))),
                                    @ApiResponse(responseCode = "415", description = "Tipo de contenido no soportado",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponseDto.class))),
                                    @ApiResponse(responseCode = "500", description = "Fallo técnico al procesar la solicitud",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponseDto.class)))
                            })),
            @RouterOperation(
                    path = "/applications/{reference}",
                    method = RequestMethod.GET,
                    beanClass = ApplicationHandler.class,
                    beanMethod = "findByReference",
                    operation = @Operation(
                            operationId = "findApplicationByReference",
                            tags = "Applications",
                            summary = "Consultar una solicitud por referencia",
                            parameters = @Parameter(
                                    name = "reference", in = ParameterIn.PATH, required = true,
                                    description = "Referencia de la solicitud procesada",
                                    schema = @Schema(type = "string"), example = "REF-002"),
                            responses = {
                                    @ApiResponse(responseCode = "200", description = "Solicitud encontrada",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ApplicationResponseDto.class))),
                                    @ApiResponse(responseCode = "400", description = "Referencia inválida",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponseDto.class))),
                                    @ApiResponse(responseCode = "404", description = "La solicitud no existe",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponseDto.class))),
                                    @ApiResponse(responseCode = "500", description = "Fallo técnico al consultar la solicitud",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponseDto.class)))
                            })),
            @RouterOperation(
                    path = "/applications",
                    method = RequestMethod.GET,
                    beanClass = ApplicationHandler.class,
                    beanMethod = "findRecent",
                    operation = @Operation(
                            operationId = "findRecentApplications",
                            tags = "Applications",
                            summary = "Listar las solicitudes recientes",
                            description = "Devuelve aprobaciones y rechazos del más reciente al más antiguo. "
                                    + "Si no existen solicitudes, devuelve una lista vacía.",
                            parameters = @Parameter(
                                    name = "limit", in = ParameterIn.QUERY,
                                    description = "Número máximo de solicitudes; entre 1 y 100",
                                    schema = @Schema(type = "integer", format = "int32",
                                            minimum = "1", maximum = "100", defaultValue = "20"), example = "20"),
                            responses = {
                                    @ApiResponse(responseCode = "200", description = "Lista de solicitudes recientes",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    array = @ArraySchema(schema = @Schema(implementation = ApplicationResponseDto.class)))),
                                    @ApiResponse(responseCode = "400", description = "El límite no es un entero entre 1 y 100",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponseDto.class))),
                                    @ApiResponse(responseCode = "500", description = "Fallo técnico al listar solicitudes",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponseDto.class)))
                            }))
    })
    public RouterFunction<ServerResponse> applicationRoutes(ApplicationHandler handler) {
        return RouterFunctions.route()
                .POST("/applications", handler::process)
                .GET("/applications/{reference}", handler::findByReference)
                .GET("/applications", handler::findRecent)
                .build();
    }
}
