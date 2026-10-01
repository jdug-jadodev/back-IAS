package com.backend_IAS.demo.infrastructure.routerhandler.router;

import com.backend_IAS.demo.application.dto.ApplicationRequestDto;
import com.backend_IAS.demo.application.dto.ApplicationPageResponseDto;
import com.backend_IAS.demo.application.dto.ApplicationResponseDto;
import com.backend_IAS.demo.application.dto.ErrorResponseDto;
import com.backend_IAS.demo.infrastructure.routerhandler.handler.ApplicationHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.headers.Header;
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
                                    + "PostgreSQL genera la referencia REF-001, REF-002… al guardar. "
                                    + "Un reintento con la misma Idempotency-Key y datos devuelve el resultado original "
                                    + "sin consumir cupo adicional. El monto se compara numéricamente.",
                            parameters = @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
                                    description = "UUID generado una vez por solicitud y reutilizado en todos sus reintentos",
                                    schema = @Schema(type = "string", format = "uuid"),
                                    example = "83b36c7f-6a2f-466a-8581-d9ac7f655038"),
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
                                    @ApiResponse(responseCode = "409", description = "La clave de idempotencia ya existe con datos diferentes",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponseDto.class))),
                                    @ApiResponse(responseCode = "413", description = "El cuerpo supera el tamaño permitido",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponseDto.class))),
                                     @ApiResponse(responseCode = "415", description = "Tipo de contenido no soportado",
                                             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                     schema = @Schema(implementation = ErrorResponseDto.class))),
                                     @ApiResponse(responseCode = "429", description = "Cuota temporal de solicitudes agotada",
                                             headers = @Header(name = "Retry-After", description = "Segundos mínimos antes de intentar de nuevo",
                                                     schema = @Schema(type = "integer", format = "int64")),
                                             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                     schema = @Schema(implementation = ErrorResponseDto.class))),
                                     @ApiResponse(responseCode = "503", description = "Tiempo de espera de base de datos agotado",
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
                                     @ApiResponse(responseCode = "503", description = "Tiempo de espera de base de datos agotado",
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
                    beanMethod = "findPage",
                    operation = @Operation(
                            operationId = "findApplicationPage",
                            tags = "Applications",
                            summary = "Consultar las solicitudes por páginas",
                            description = "Devuelve aprobaciones y rechazos del más reciente al más antiguo. "
                                    + "Incluye totales. Una página sin resultados devuelve content vacío. "
                                    + "Los parámetros son page y size; limit ya no está disponible.",
                            parameters = {
                                    @Parameter(name = "page", in = ParameterIn.QUERY,
                                            description = "Índice de página, comienza en cero",
                                            schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0")),
                                    @Parameter(name = "size", in = ParameterIn.QUERY,
                                            description = "Máximo de solicitudes por página, entre 1 y 100",
                                            schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
                            },
                            responses = {
                                    @ApiResponse(responseCode = "200", description = "Página de solicitudes y totales",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ApplicationPageResponseDto.class))),
                                     @ApiResponse(responseCode = "400", description = "Parámetros de paginación inválidos o uso de limit",
                                             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                     schema = @Schema(implementation = ErrorResponseDto.class))),
                                     @ApiResponse(responseCode = "503", description = "Tiempo de espera de base de datos agotado",
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
                .GET("/applications", handler::findPage)
                .build();
    }
}
