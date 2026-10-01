package com.backend_IAS.demo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.rate-limit.enabled=true", "app.rate-limit.capacity=2", "app.rate-limit.refill-period=1h"
})
class RateLimitingIntegrationTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withInitScript("persistence-test-schema.sql");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + POSTGRES.getHost()
                + ":" + POSTGRES.getMappedPort(5432) + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    @Value("${local.server.port}")
    private int port;
    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void shouldRejectExcessPostsBeforePersistenceAndKeepReadEndpointsAvailable() {
        WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        String key = UUID.randomUUID().toString();
        Map<String, Object> request = Map.of("customerId", "CLI-1001",
                "amount", "1000", "termMonths", 12);
        client.post().uri("/applications;channel=web").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).bodyValue(request).exchange()
                .expectStatus().isCreated();
        client.post().uri("/applications").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).bodyValue(request).exchange()
                .expectStatus().isOk();

        var limited = client.post().uri("/applications").header("X-Forwarded-For", "198.51.100.42")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("applicationReference", "RATE-REJECTED", "customerId", "CLI-1001",
                        "amount", "1000", "termMonths", 12)).exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().value("Retry-After", value -> {
                    long seconds = Long.parseLong(value);
                    assertTrue(seconds > 0 && seconds <= 1800);
                })
                .expectBody(Map.class).returnResult();
        assertEquals("RATE_LIMIT_EXCEEDED", limited.getResponseBody().get("code"));
        UUID.fromString((String) limited.getResponseBody().get("traceId"));
        assertEquals(limited.getResponseBody().get("traceId"), limited.getResponseHeaders().getFirst("X-Trace-Id"));
        client.post().uri("/applications;channel=web").contentType(MediaType.APPLICATION_JSON).bodyValue(request)
                .exchange().expectStatus().isEqualTo(429);
        client.get().uri("/applications/REF-001").exchange().expectStatus().isOk();
        client.get().uri("/applications").exchange().expectStatus().isOk();
        client.get().uri("/customers/CLI-1001/credit-summary").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.approvedAmount").isEqualTo("1000");
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
        assertEquals(1L, databaseClient.sql("SELECT COUNT(*) AS total FROM credit_applications")
                .map(row -> row.get("total", Long.class)).one().block(Duration.ofSeconds(10)));
    }
}
