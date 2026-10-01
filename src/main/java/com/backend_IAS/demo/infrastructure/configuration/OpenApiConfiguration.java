package com.backend_IAS.demo.infrastructure.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "API de créditos IAS",
                version = "1.0.0",
                description = "Procesamiento y consulta de solicitudes de crédito. "
                        + "Los rechazos de negocio se guardan y responden HTTP 201. "
                        + "Los errores incluyen traceId y la cabecera X-Trace-Id con el mismo valor."),
        tags = {
                @Tag(name = "Applications", description = "Solicitudes de crédito"),
                @Tag(name = "Customers", description = "Consulta del cupo de clientes")
        })
public class OpenApiConfiguration {
}
