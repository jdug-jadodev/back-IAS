package com.backend_IAS.demo.infrastructure.routerhandler.router;

import com.backend_IAS.demo.infrastructure.routerhandler.handler.ApplicationHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class ApplicationRouter {

    @Bean
    public RouterFunction<ServerResponse> applicationRoutes(ApplicationHandler handler) {
        return RouterFunctions.route()
                .POST("/applications", handler::process)
                .GET("/applications/{reference}", handler::findByReference)
                .GET("/applications", handler::findRecent)
                .build();
    }
}
