package com.backend_IAS.demo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.infrastructure.r2dbc.adapter.ApplicationR2dbcAdapter;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.rate-limit.enabled=false", "spring.r2dbc.pool.max-size=4", "spring.r2dbc.pool.initial-size=0"
})
class ApplicationFlowsIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("credit_flow_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("flow-test-user.sql");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + POSTGRES.getHost()
                + ":" + POSTGRES.getMappedPort(5432) + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", () -> "flow_app");
        registry.add("spring.r2dbc.password", () -> "flow_password");
    }

    @Value("${local.server.port}")
    private int serverPort;

    @Autowired
    private DatabaseClient applicationDatabaseClient;

    @MockitoSpyBean
    private ApplicationR2dbcAdapter applicationAdapter;

    @MockitoSpyBean(name = "connectionFactory")
    private ConnectionFactory applicationConnectionFactory;

    private DatabaseClient adminDatabaseClient;
    private ConnectionFactory adminConnectionFactory;
    private WebTestClient client;
    private WebClient concurrentClient;

    @BeforeEach
    void initializeIsolatedDatabase() {
        adminConnectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, POSTGRES.getHost())
                .option(ConnectionFactoryOptions.PORT, POSTGRES.getMappedPort(5432))
                .option(ConnectionFactoryOptions.DATABASE, POSTGRES.getDatabaseName())
                .option(ConnectionFactoryOptions.USER, POSTGRES.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, POSTGRES.getPassword())
                .build());
        adminDatabaseClient = DatabaseClient.create(adminConnectionFactory);
        new ResourceDatabasePopulator(new ClassPathResource("persistence-test-schema.sql"))
                .populate(adminConnectionFactory).block(TIMEOUT);
        adminDatabaseClient.sql("GRANT SELECT, UPDATE (status) ON customers TO flow_app").then()
                .then(adminDatabaseClient.sql("GRANT SELECT, INSERT ON credit_applications TO flow_app").then())
                .then(adminDatabaseClient.sql("GRANT USAGE, SELECT ON SEQUENCE credit_applications_id_seq, credit_applications_reference_seq TO flow_app").then())
                .block(TIMEOUT);
        String baseUrl = "http://localhost:" + serverPort;
        client = WebTestClient.bindToServer().baseUrl(baseUrl).responseTimeout(TIMEOUT).build();
        concurrentClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Test
    void shouldReportHealthyMonolithWithoutExposingComponentDetails() {
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody(RESPONSE_TYPE)
                .value(body -> {
                    assertEquals("UP", body.get("status"));
                    assertEquals(List.of("liveness", "readiness"), body.get("groups"));
                    assertFalse(body.containsKey("components"));
                    assertFalse(body.containsKey("details"));
                });
    }

    @Test
    void shouldReportServiceUnavailableWhenDatabaseConnectionFails() {
        doReturn(Mono.error(new R2dbcNonTransientResourceException("Controlled connection failure")))
                .when(applicationConnectionFactory).create();

        client.get().uri("/actuator/health").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(RESPONSE_TYPE)
                .value(body -> {
                    assertEquals("DOWN", body.get("status"));
                    assertEquals(List.of("liveness", "readiness"), body.get("groups"));
                    assertFalse(body.containsKey("components"));
                    assertFalse(body.containsKey("details"));
                });
    }

    @Test
    void shouldDocumentMonolithHealthInOpenApi() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/actuator/health'].get").exists();
    }

    @ParameterizedTest
    @CsvSource({"CLI-1001, ELIGIBLE, 10000000", "CLI-1002, BLOCKED, 8000000"})
    void shouldQueryCreditSummaryWithoutApprovals(String customerId, String status, String limit) {
        Map<String, Object> summary = creditSummary(customerId);
        assertEquals(customerId, summary.get("customerId"));
        assertEquals(status, summary.get("status"));
        assertDecimal(limit, summary.get("creditLimit"));
        assertDecimal("0", summary.get("approvedAmount"));
        assertDecimal(limit, summary.get("availableAmount"));
    }

    @Test
    void shouldSummarizePartialApprovalsWithoutCountingRejectionsOrRetries() {
        process(request("SUMMARY-FIRST", "CLI-1001", "6000000.00", 12), 201);
        process(request("SUMMARY-FIRST", "CLI-1001", "6000000", 12), 200);
        process(request("SUMMARY-REJECTED", "CLI-1001", "5000000", 12), 201);
        process(request("SUMMARY-SECOND", "CLI-1001", "1000000", 12), 201);
        process(request("SUMMARY-OTHER", "CLI-2001", "1000000", 12), 201);
        Map<String, Object> summary = creditSummary("CLI-1001");
        assertDecimal("7000000", summary.get("approvedAmount"));
        assertDecimal("3000000", summary.get("availableAmount"));
    }

    @Test
    void shouldReportZeroAvailableCreditAtExactLimit() {
        process(request("SUMMARY-EXACT", "CLI-1001", "10000000", 12), 201);
        assertDecimal("0", creditSummary("CLI-1001").get("availableAmount"));
    }

    @Test
    void shouldPreserveApprovedTotalWhenCustomerLimitIsReduced() {
        process(request("SUMMARY-REDUCED", "CLI-1001", "6000000", 12), 201);
        adminDatabaseClient.sql("UPDATE customers SET approval_limit = 5000000 WHERE customer_id = 'CLI-1001'")
                .then().block(TIMEOUT);
        Map<String, Object> summary = creditSummary("CLI-1001");
        assertDecimal("5000000", summary.get("creditLimit"));
        assertDecimal("6000000", summary.get("approvedAmount"));
        assertDecimal("0", summary.get("availableAmount"));
    }

    @Test
    void shouldPreserveDecimalPrecisionInCreditSummary() {
        process(request("SUMMARY-DECIMAL", "CLI-1001", "0.1234567890123456789", 12), 201);
        Map<String, Object> summary = creditSummary("CLI-1001");
        assertEquals("0.1234567890123456789", summary.get("approvedAmount"));
        assertEquals("9999999.8765432109876543211", summary.get("availableAmount"));
    }

    @Test
    void shouldReturnCustomerNotFoundForCreditSummary() {
        assertError(client.get().uri("/customers/CLI-MISSING/credit-summary").exchange(), 404, "CUSTOMER_NOT_FOUND");
        assertEquals(0L, applicationCount());
    }

    @Test
    void shouldRejectBlankCustomerInCreditSummary() {
        assertError(client.get().uri("/customers/{customerId}/credit-summary", " ").exchange(),
                400, "INVALID_APPLICATION_DATA");
    }

    @Test
    void shouldDocumentCreditSummaryInOpenApi() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths['/customers/{customerId}/credit-summary'].get").exists()
                .jsonPath("$.paths['/customers/{customerId}/credit-summary'].get.responses['503']").exists()
                .jsonPath("$.paths['/applications'].post.responses['429'].headers['Retry-After']").exists();
    }

    @Test
    void shouldReadCreditSummaryWhileCustomerIsLocked() {
        Connection connection = Mono.from(adminConnectionFactory.create()).block(TIMEOUT);
        assertNotNull(connection);
        try {
            lockCustomer(connection, "CLI-1001");
            assertDecimal("10000000", creditSummary("CLI-1001").get("availableAmount"));
        } finally {
            Mono.from(connection.rollbackTransaction()).then(Mono.from(connection.close())).block(TIMEOUT);
        }
    }

    @Test
    void shouldTimeoutCustomerLockWithoutPersistingAndAllowRetryAfterRelease() {
        Connection connection = Mono.from(adminConnectionFactory.create()).block(TIMEOUT);
        assertNotNull(connection);
        try {
            lockCustomer(connection, "CLI-1001");
            assertError(post(request("TIMEOUT-LOCK", "CLI-1001", "1000", 12)), 503, "DATABASE_TIMEOUT");
            assertEquals(0L, applicationCount());
        } finally {
            Mono.from(connection.rollbackTransaction()).then(Mono.from(connection.close())).block(TIMEOUT);
        }
        assertEquals("APPROVED", process(request("TIMEOUT-LOCK", "CLI-1001", "1000", 12), 201).get("status"));
        assertEquals(1L, applicationCount());
    }

    @Test
    void shouldTimeoutSlowStatementAndRollbackBeforeAllowingRetry() {
        adminDatabaseClient.sql("""
                CREATE OR REPLACE FUNCTION flow_delay_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$
                BEGIN
                    PERFORM pg_sleep(15);
                    RETURN NEW;
                END
                $$
                """).then().block(TIMEOUT);
        adminDatabaseClient.sql("""
                CREATE TRIGGER flow_delay_insert BEFORE INSERT ON credit_applications
                FOR EACH ROW EXECUTE FUNCTION flow_delay_insert()
                """).then().block(TIMEOUT);
        try {
            assertError(post(request("TIMEOUT-STATEMENT", "CLI-1001", "1000", 12)), 503, "DATABASE_TIMEOUT");
            assertEquals(0L, applicationCount());
            assertEquals(0, totalApproved("CLI-1001").signum());
        } finally {
            adminDatabaseClient.sql("DROP TRIGGER flow_delay_insert ON credit_applications").then()
                    .then(adminDatabaseClient.sql("DROP FUNCTION flow_delay_insert()").then()).block(TIMEOUT);
        }
        assertEquals("APPROVED", process(request("TIMEOUT-STATEMENT", "CLI-1001", "1000", 12), 201).get("status"));
    }

    @Test
    void shouldTimeoutExhaustedPoolAndRecoverAfterConnectionsAreReleased() {
        List<Connection> heldConnections = new ArrayList<>();
        try {
            for (int index = 0; index < 4; index++) {
                heldConnections.add(Mono.from(applicationConnectionFactory.create()).block(TIMEOUT));
            }
            assertError(post(request("TIMEOUT-POOL", "CLI-1001", "1000", 12)), 503, "DATABASE_TIMEOUT");
            assertEquals(0L, applicationCount());
        } finally {
            for (Connection connection : heldConnections) {
                Mono.from(connection.close()).block(TIMEOUT);
            }
        }
        assertEquals("APPROVED", process(request("TIMEOUT-POOL", "CLI-1001", "1000", 12), 201).get("status"));
    }

    @Test
    void shouldUseRestrictedApplicationDatabaseUser() {
        StepVerifier.create(applicationDatabaseClient.sql("""
                        SELECT current_user AS username,
                            has_table_privilege(current_user, 'credit_applications', 'UPDATE') AS can_update,
                            has_table_privilege(current_user, 'credit_applications', 'DELETE') AS can_delete
                        """)
                .map((row, metadata) -> {
                    assertEquals("flow_app", row.get("username", String.class));
                    assertFalse(Boolean.TRUE.equals(row.get("can_update", Boolean.class)));
                    assertFalse(Boolean.TRUE.equals(row.get("can_delete", Boolean.class)));
                    return true;
                }).one())
                .expectNext(true)
                .expectComplete()
                .verify(TIMEOUT);
    }

    @Test
    void shouldApprovePersistAndQueryOriginalDecision() {
        Map<String, Object> response = process(request("FLOW-APPROVED", "CLI-1001", "1000000.00", 12), 201);

        assertEquals("APPROVED", response.get("status"));
        assertEquals("Esta solicitud fue aprobada", response.get("message"));
        assertEquals("1000000.00", response.get("amount"));
        assertNull(response.get("reasonCode"));
        assertNull(response.get("reason"));
        assertNotNull(Instant.parse((String) response.get("processedAt")));
        assertEquals(response, findByReference((String) response.get("applicationReference")));
        assertEquals("REF-001", response.get("applicationReference"));
        assertFalse(response.containsKey("idempotencyKey"));
        assertEquals(1L, applicationCount());
        assertEquals(0, totalApproved("CLI-1001").compareTo(new BigDecimal("1000000")));
    }

    @Test
    void shouldPreserveDecimalPrecisionThroughHttpAndPersistence() {
        String amount = "1000000.123456789123456789";

        Map<String, Object> response = process(request("FLOW-PRECISION", "CLI-1001", amount, 12), 201);

        assertEquals(amount, response.get("amount"));
        assertEquals(amount, findByKey("FLOW-PRECISION").get("amount"));
        assertEquals(new BigDecimal(amount), totalApproved("CLI-1001"));
    }

    @Test
    void shouldAccumulatePartialApprovalsAndAllowExactCreditLimit() {
        List<Map<String, Object>> approvals = new ArrayList<>();
        approvals.add(process(request("FLOW-PART-1", "CLI-1001", "5000000", 6), 201));
        approvals.add(process(request("FLOW-PART-2", "CLI-1001", "1000000", 12), 201));
        approvals.add(process(request("FLOW-PART-3", "CLI-1001", "2000000", 24), 201));
        approvals.add(process(request("FLOW-PART-4", "CLI-1001", "2000000", 60), 201));

        assertTrue(approvals.stream().allMatch(response -> response.get("status").equals("APPROVED")));
        Map<String, Object> rejected = process(request("FLOW-OVER-LIMIT", "CLI-1001", "1", 12), 201);
        assertEquals("REJECTED", rejected.get("status"));
        assertEquals("INSUFFICIENT_LIMIT", rejected.get("reasonCode"));
        assertEquals(0, totalApproved("CLI-1001").compareTo(new BigDecimal("10000000")));
        assertEquals(5L, applicationCount());

        Map<String, Object> retried = process(request("FLOW-PART-1", "CLI-1001", "5000000.00", 6), 200);
        assertEquals("APPROVED", retried.get("status"));
        assertEquals("Esta solicitud ya fue aprobada", retried.get("message"));
        assertEquals(approvals.getFirst().get("processedAt"), retried.get("processedAt"));
        assertEquals(5L, applicationCount());
        assertEquals(0, totalApproved("CLI-1001").compareTo(new BigDecimal("10000000")));
    }

    @ParameterizedTest
    @CsvSource({
            "CLI-1001, 0, 12, INVALID_AMOUNT, El monto debe ser mayor que cero",
            "CLI-1001, -1000, 12, INVALID_AMOUNT, El monto debe ser mayor que cero",
            "CLI-1001, 1000, 5, INVALID_TERM, El plazo debe estar entre 6 y 60 meses",
            "CLI-1001, 1000, 61, INVALID_TERM, El plazo debe estar entre 6 y 60 meses",
            "CLI-1002, 1000, 12, CUSTOMER_BLOCKED, El cliente no está habilitado",
            "CLI-MISSING, 1000, 12, CUSTOMER_NOT_FOUND, El cliente no existe",
            "CLI-1001, 10000001, 12, INSUFFICIENT_LIMIT, El cupo disponible es insuficiente"
    })
    void shouldPersistBusinessRejectionWithoutConsumingCredit(
            String customerId, String amount, int termMonths, String reasonCode, String reason) {
        Map<String, Object> response = process(request("FLOW-REJECTED", customerId, amount, termMonths), 201);

        assertEquals("REJECTED", response.get("status"));
        assertEquals("Esta solicitud fue rechazada", response.get("message"));
        assertEquals(reasonCode, response.get("reasonCode"));
        assertEquals(reason, response.get("reason"));
        assertEquals(customerId, response.get("customerId"));
        assertEquals(response, findByKey("FLOW-REJECTED"));
        assertEquals(1L, applicationCount());
        assertEquals(0, totalApproved(customerId).signum());
    }

    @ParameterizedTest
    @CsvSource({
            "CLI-1002, -1, 5, INVALID_AMOUNT",
            "CLI-1002, 1, 5, INVALID_TERM",
            "CLI-1002, 100000000, 12, CUSTOMER_BLOCKED",
            "CLI-MISSING, -1, 5, CUSTOMER_NOT_FOUND"
    })
    void shouldApplyBusinessRejectionPrecedence(String customerId, String amount, int termMonths, String reasonCode) {
        Map<String, Object> response = process(request("FLOW-PRECEDENCE", customerId, amount, termMonths), 201);

        assertEquals("REJECTED", response.get("status"));
        assertEquals(reasonCode, response.get("reasonCode"));
        assertEquals(1L, applicationCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLI-1001", "CLI-1002", "CLI-MISSING"})
    void shouldReturnIdenticalRetriesWithoutDuplicatingOrReevaluating(String customerId) {
        Map<String, Object> original = process(request("FLOW-RETRY", customerId, "1000000.00", 12), 201);

        for (int attempt = 0; attempt < 3; attempt++) {
            Map<String, Object> retried = process(request("FLOW-RETRY", customerId, "1000000", 12), 200);
            Map<String, Object> expected = new HashMap<>(original);
            expected.put("message", original.get("status").equals("APPROVED")
                    ? "Esta solicitud ya fue aprobada" : "Esta solicitud ya fue rechazada");
            assertEquals(expected, retried);
        }
        assertEquals(1L, applicationCount());
        assertEquals(original, findByKey("FLOW-RETRY"));
        assertEquals(0, totalApproved(customerId).compareTo(customerId.equals("CLI-1001")
                ? new BigDecimal("1000000") : BigDecimal.ZERO));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLI-1002", "CLI-MISSING"})
    void shouldPreserveHistoricalRejectionWhenCustomerLaterBecomesEligible(String customerId) {
        Map<String, Object> original = process(request("FLOW-HISTORICAL", customerId, "1000", 12), 201);
        if (customerId.equals("CLI-MISSING")) {
            adminDatabaseClient.sql("INSERT INTO customers VALUES ('CLI-MISSING', 'ELIGIBLE', 10000000)")
                    .then().block(TIMEOUT);
        } else {
            adminDatabaseClient.sql("UPDATE customers SET status = 'ELIGIBLE' WHERE customer_id = :customerId")
                    .bind("customerId", customerId).then().block(TIMEOUT);
        }

        Map<String, Object> retried = process(request("FLOW-HISTORICAL", customerId, "1000", 12), 200);

        Map<String, Object> expected = new HashMap<>(original);
        expected.put("message", "Esta solicitud ya fue rechazada");
        assertEquals(expected, retried);
        assertEquals(original, findByKey("FLOW-HISTORICAL"));
        assertEquals(1L, applicationCount());
        assertEquals(0, totalApproved(customerId).signum());
    }

    @ParameterizedTest
    @CsvSource({"CLI-2001, 1000000, 12", "CLI-1001, 1000001, 12", "CLI-1001, 1000000, 24"})
    void shouldRejectKeyReuseWithChangedDataWithoutOverwritingOriginal(
            String customerId, String amount, int termMonths) {
        Map<String, Object> original = process(request("FLOW-CONFLICT", "CLI-1001", "1000000", 12), 201);

        assertError(post(request("FLOW-CONFLICT", customerId, amount, termMonths)), 409, "IDEMPOTENCY_CONFLICT");

        assertEquals(original, findByKey("FLOW-CONFLICT"));
        assertEquals(1L, applicationCount());
        assertEquals(0, totalApproved("CLI-1001").compareTo(new BigDecimal("1000000")));
        assertEquals(0, totalApproved("CLI-2001").signum());
    }

    @ParameterizedTest
    @ValueSource(strings = {"idempotencyKey", "customerId", "amount", "termMonths"})
    void shouldRejectMissingRequiredFieldsWithoutPersisting(String missingField) {
        Map<String, Object> data = new HashMap<>(request("FLOW-MISSING", "CLI-1001", "1000", 12));
        data.remove(missingField);

        assertError(post(data), 400, "INVALID_APPLICATION_DATA");

        assertEquals(0L, applicationCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"idempotencyKey", "customerId"})
    void shouldRejectBlankIdentifiersWithoutPersisting(String blankField) {
        Map<String, Object> data = new HashMap<>(request("FLOW-BLANK", "CLI-1001", "1000", 12));
        data.put(blankField, blankField.equals("idempotencyKey") ? "" : " \t\n ");

        assertError(post(data), 400, "INVALID_APPLICATION_DATA");

        assertEquals(0L, applicationCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"applicationReference\":",
            "{\"applicationReference\":\"FLOW-TYPE\",\"customerId\":\"CLI-1001\",\"amount\":\"abc\",\"termMonths\":12}",
            "{\"applicationReference\":\"FLOW-TYPE\",\"customerId\":\"CLI-1001\",\"amount\":\"1000\",\"termMonths\":\"abc\"}"
    })
    void shouldRejectMalformedJsonAndInvalidAmountTypes(String body) {
        assertError(client.post().uri("/applications").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body).exchange(), 400, "INVALID_REQUEST");

        assertEquals(0L, applicationCount());
    }

    @Test
    void shouldRejectEmptyRequestBodyWithoutPersisting() {
        assertError(client.post().uri("/applications").contentType(MediaType.APPLICATION_JSON).exchange(),
                400, "INVALID_REQUEST");

        assertEquals(0L, applicationCount());
    }

    @Test
    void shouldRejectUnsupportedContentTypeWithoutPersisting() {
        assertError(client.post().uri("/applications").contentType(MediaType.TEXT_PLAIN)
                .bodyValue("Invalid content type").exchange(), 415, "UNSUPPORTED_MEDIA_TYPE");

        assertEquals(0L, applicationCount());
    }

    @Test
    void shouldReturnNotFoundWithTraceCorrelation() {
        assertError(client.get().uri("/applications/FLOW-MISSING").exchange(), 404, "APPLICATION_NOT_FOUND");
    }

    @Test
    void shouldPageDecisionsWithTotalAndStableTieBreaker() {
        process(request("FLOW-RECENT-1", "CLI-1001", "1000", 12), 201);
        process(request("FLOW-RECENT-2", "CLI-1002", "1000", 12), 201);
        process(request("FLOW-RECENT-3", "CLI-MISSING", "1000", 12), 201);
        adminDatabaseClient.sql("UPDATE credit_applications SET processed_at = '2026-10-01T12:00:00Z'")
                .then().block(TIMEOUT);

        Map<String, Object> first = applicationPage(0, 2);
        assertPageMetadata(first, 0, 2, 3, 2, true, false);
        List<Map<String, Object>> recent = pageContent(first);
        assertEquals(List.of("REF-003", "REF-002"),
                recent.stream().map(response -> response.get("applicationReference")).toList());
        assertTrue(recent.stream().allMatch(response -> response.get("status").equals("REJECTED")));
        Map<String, Object> last = applicationPage(1, 2);
        assertPageMetadata(last, 1, 2, 3, 2, false, true);
        assertEquals(List.of("REF-001"), pageContent(last).stream()
                .map(response -> response.get("applicationReference")).toList());
        assertEquals("APPROVED", pageContent(last).getFirst().get("status"));
        assertEquals("Esta solicitud fue aprobada", pageContent(last).getFirst().get("message"));
        assertEquals("REF-001", findByReference("REF-001").get("applicationReference"));
    }

    @Test
    void shouldReturnAnEmptyPageWithDefaultPaginationWhenNoApplicationsExist() {
        client.get().uri("/applications").exchange().expectStatus().isOk()
                .expectBody(RESPONSE_TYPE).value(response -> {
                    assertPageMetadata(response, 0, 20, 0, 0, true, true);
                    assertTrue(pageContent(response).isEmpty());
                });
    }

    @ParameterizedTest
    @CsvSource({
            "page=-1, INVALID_APPLICATION_DATA", "size=0, INVALID_APPLICATION_DATA",
            "size=-1, INVALID_APPLICATION_DATA", "size=101, INVALID_APPLICATION_DATA",
            "page=abc, INVALID_REQUEST", "size=abc, INVALID_REQUEST",
            "page=1.5, INVALID_REQUEST", "size=1.5, INVALID_REQUEST",
            "page=2147483648, INVALID_REQUEST", "size=2147483648, INVALID_REQUEST"
    })
    void shouldRejectInvalidPagination(String query, String errorCode) {
        assertError(client.get().uri("/applications?" + query).exchange(), 400, errorCode);
    }

    @Test
    void shouldRejectLegacyLimitWithAnExplicitMigrationMessage() {
        Map<String, Object> error = assertError(client.get().uri("/applications?limit=20").exchange(),
                400, "INVALID_APPLICATION_DATA");
        assertEquals("El parámetro limit ya no está disponible; utiliza page y size", error.get("message"));
    }

    @Test
    void shouldDefaultToTwentyAndAllowIndependentPageAndSizeDefaults() {
        for (int index = 1; index <= 21; index++) {
            process(request("FLOW-PAGE-" + index, "CLI-MISSING", "1000", 12), 201);
        }
        client.get().uri("/applications").exchange().expectStatus().isOk().expectBody(RESPONSE_TYPE)
                .value(response -> {
                    assertPageMetadata(response, 0, 20, 21, 2, true, false);
                    assertEquals(20, pageContent(response).size());
                });
        client.get().uri("/applications?page=1").exchange().expectStatus().isOk().expectBody(RESPONSE_TYPE)
                .value(response -> {
                    assertPageMetadata(response, 1, 20, 21, 2, false, true);
                    assertEquals(1, pageContent(response).size());
                });
        client.get().uri("/applications?size=1").exchange().expectStatus().isOk().expectBody(RESPONSE_TYPE)
                .value(response -> {
                    assertPageMetadata(response, 0, 1, 21, 21, true, false);
                    assertEquals(1, pageContent(response).size());
                });
    }

    @Test
    void shouldReturnEmptyContentBeyondLastPageWithoutLosingTotals() {
        process(request("FLOW-PAGE-SINGLE", "CLI-1001", "1000", 12), 201);
        Map<String, Object> response = applicationPage(3, 2);
        assertPageMetadata(response, 3, 2, 1, 1, false, true);
        assertTrue(pageContent(response).isEmpty());
        Map<String, Object> largeOffset = applicationPage(Integer.MAX_VALUE, 100);
        assertPageMetadata(largeOffset, Integer.MAX_VALUE, 100, 1, 1, false, true);
        assertTrue(pageContent(largeOffset).isEmpty());
    }

    @Test
    void shouldMarkAnExactFullPageAsLastAndPreserveDecimalPrecision() {
        process(request("FLOW-PAGE-DECIMAL", "CLI-1001", "0.1234567890123456789", 12), 201);
        Map<String, Object> response = applicationPage(0, 1);
        assertPageMetadata(response, 0, 1, 1, 1, true, true);
        assertEquals("0.1234567890123456789", pageContent(response).getFirst().get("amount"));
    }

    @Test
    void shouldKeepPaginationTotalsUnchangedOnIdenticalRetry() {
        Map<String, Object> data = request("FLOW-PAGE-RETRY", "CLI-1001", "1000", 12);
        process(data, 201);
        Map<String, Object> beforeRetry = applicationPage(0, 20);
        process(data, 200);
        assertEquals(beforeRetry, applicationPage(0, 20));
    }

    @Test
    void shouldDocumentPaginationParametersAndObjectResponse() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths['/applications'].get.parameters[0].name").isEqualTo("page")
                .jsonPath("$.paths['/applications'].get.parameters[1].name").isEqualTo("size")
                .jsonPath("$.components.schemas.ApplicationPageResponseDto.properties.content.type").isEqualTo("array")
                .jsonPath("$.components.schemas.ApplicationPageResponseDto.properties.totalElements.type").isEqualTo("integer");
    }

    @Test
    void shouldReportPageReadFailuresRatherThanReturningAnEmptyPage() {
        adminDatabaseClient.sql("DROP TABLE credit_applications").then().block(TIMEOUT);
        assertError(client.get().uri("/applications?page=0&size=20").exchange(), 500, "INTERNAL_ERROR");
    }

    @Test
    void shouldReturnTechnicalFailureRatherThanACreditRejectionWhenPersistenceFails() {
        adminDatabaseClient.sql("DROP TABLE credit_applications").then().block(TIMEOUT);

        Map<String, Object> error = assertError(post(request("FLOW-DB-FAILURE", "CLI-1001", "1000", 12)),
                500, "INTERNAL_ERROR");

        assertEquals("No fue posible procesar la petición", error.get("message"));
        assertFalse(error.containsKey("reasonCode"));
        assertFalse(error.containsKey("status"));
    }

    @Test
    void shouldRollbackInsertionFailureAndAllowSubsequentProcessing() {
        adminDatabaseClient.sql("""
                ALTER TABLE credit_applications ADD CONSTRAINT ck_flow_insert_failure
                CHECK (application_reference <> 'REF-001')
                """).then().block(TIMEOUT);

        assertError(post(request("FLOW-INSERT-FAILURE", "CLI-1001", "1000", 12)), 500, "INTERNAL_ERROR");

        assertEquals(0L, applicationCount());
        assertEquals(0, totalApproved("CLI-1001").signum());
        assertEquals("APPROVED", process(request("FLOW-AFTER-INSERT-FAILURE", "CLI-1001", "1000", 12), 201).get("status"));
        assertEquals(1L, applicationCount());
        assertEquals(0, totalApproved("CLI-1001").compareTo(new BigDecimal("1000")));
    }

    @Test
    void shouldReturnNoSuccessfulDecisionWhenTransactionCommitFails() {
        adminDatabaseClient.sql("CREATE TABLE flow_allowed_customers (customer_id TEXT PRIMARY KEY)")
                .then().block(TIMEOUT);
        try {
            adminDatabaseClient.sql("""
                    ALTER TABLE credit_applications ADD CONSTRAINT fk_flow_commit_failure
                    FOREIGN KEY (customer_id) REFERENCES flow_allowed_customers(customer_id)
                    DEFERRABLE INITIALLY DEFERRED
                    """).then().block(TIMEOUT);

            assertError(post(request("FLOW-COMMIT-FAILURE", "CLI-1001", "1000", 12)), 500, "INTERNAL_ERROR");

            verify(applicationAdapter).insert(any(CreditApplication.class));
            assertEquals(0L, applicationCount());
            assertEquals(0, totalApproved("CLI-1001").signum());
        } finally {
            adminDatabaseClient.sql("DROP TABLE flow_allowed_customers CASCADE").then().block(TIMEOUT);
        }
        assertEquals("APPROVED", process(request("FLOW-AFTER-COMMIT-FAILURE", "CLI-1001", "1000", 12), 201).get("status"));
        assertEquals(1L, applicationCount());
    }

    @Test
    void shouldProtectCreditLimitAcrossConcurrentDifferentKeys() {
        Mono<ResponseEntity<Map<String, Object>>> first = send(request("FLOW-CONCURRENT-A", "CLI-2001", "10000000", 12));
        Mono<ResponseEntity<Map<String, Object>>> second = send(request("FLOW-CONCURRENT-B", "CLI-2001", "10000000", 12));

        StepVerifier.create(Mono.zip(first, second))
                .assertNext(pair -> {
                    assertEquals(HttpStatus.CREATED, pair.getT1().getStatusCode());
                    assertEquals(HttpStatus.CREATED, pair.getT2().getStatusCode());
                    List<Map<String, Object>> decisions = List.of(pair.getT1().getBody(), pair.getT2().getBody());
                    assertEquals(1L, decisions.stream().filter(body -> body.get("status").equals("APPROVED")).count());
                    assertEquals(1L, decisions.stream().filter(body -> "INSUFFICIENT_LIMIT".equals(body.get("reasonCode"))).count());
                })
                .expectComplete().verify(TIMEOUT);

        assertEquals(2L, applicationCount());
        assertEquals(0, totalApproved("CLI-2001").compareTo(new BigDecimal("10000000")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLI-1001", "CLI-MISSING"})
    void shouldRecoverConcurrentIdenticalKeysAfterRollback(String customerId) {
        synchronizeInitialKeyLookups("FLOW-DUPLICATE");
        Mono<ResponseEntity<Map<String, Object>>> first = send(request("FLOW-DUPLICATE", customerId, "10000000.00", 12));
        Mono<ResponseEntity<Map<String, Object>>> second = send(request("FLOW-DUPLICATE", customerId, "10000000", 12));

        StepVerifier.create(Mono.zip(first, second))
                .assertNext(pair -> {
                    List<ResponseEntity<Map<String, Object>>> responses = List.of(pair.getT1(), pair.getT2());
                    assertEquals(1L, responses.stream().filter(response -> response.getStatusCode().value() == 201).count());
                    assertEquals(1L, responses.stream().filter(response -> response.getStatusCode().value() == 200).count());
                    Map<String, Object> original = new HashMap<>(responses.stream()
                            .filter(response -> response.getStatusCode().value() == 201).findFirst().orElseThrow().getBody());
                    Map<String, Object> retried = responses.stream()
                            .filter(response -> response.getStatusCode().value() == 200).findFirst().orElseThrow().getBody();
                    original.put("message", customerId.equals("CLI-1001")
                            ? "Esta solicitud ya fue aprobada" : "Esta solicitud ya fue rechazada");
                    assertEquals(original, retried);
                })
                .expectComplete().verify(TIMEOUT);

        verify(applicationAdapter, times(2)).insert(any(CreditApplication.class));
        verify(applicationAdapter, times(3)).findByIdempotencyKey(key("FLOW-DUPLICATE"));
        assertEquals(1L, applicationCount());
        assertEquals(0, totalApproved(customerId).compareTo(customerId.equals("CLI-1001")
                ? new BigDecimal("10000000") : BigDecimal.ZERO));
    }

    @Test
    void shouldReturnConflictForConcurrentKeyReuseAcrossCustomers() {
        synchronizeInitialKeyLookups("FLOW-CROSS-CUSTOMER");
        Mono<ResponseEntity<Map<String, Object>>> first = send(request("FLOW-CROSS-CUSTOMER", "CLI-1001", "1000000", 12));
        Mono<ResponseEntity<Map<String, Object>>> second = send(request("FLOW-CROSS-CUSTOMER", "CLI-2001", "1000000", 12));

        StepVerifier.create(Mono.zip(first, second))
                .assertNext(pair -> {
                    List<ResponseEntity<Map<String, Object>>> responses = List.of(pair.getT1(), pair.getT2());
                    assertEquals(1L, responses.stream().filter(response -> response.getStatusCode().value() == 201).count());
                    assertEquals(1L, responses.stream().filter(response -> response.getStatusCode().value() == 409).count());
                    ResponseEntity<Map<String, Object>> conflict = responses.stream()
                            .filter(response -> response.getStatusCode().value() == 409).findFirst().orElseThrow();
                    assertEquals("IDEMPOTENCY_CONFLICT", conflict.getBody().get("code"));
                    assertEquals(conflict.getBody().get("traceId"), conflict.getHeaders().getFirst("X-Trace-Id"));
                })
                .expectComplete().verify(TIMEOUT);

        verify(applicationAdapter, times(2)).insert(any(CreditApplication.class));
        assertEquals(1L, applicationCount());
        assertEquals(0, totalApproved("CLI-1001").add(totalApproved("CLI-2001")).compareTo(new BigDecimal("1000000")));
        assertEquals("APPROVED", findByKey("FLOW-CROSS-CUSTOMER").get("status"));
    }

    private Map<String, Object> request(String reference, String customerId, String amount, int termMonths) {
        return Map.of("idempotencyKey", key(reference), "customerId", customerId, "amount", amount, "termMonths", termMonths);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "1-1-1-1-1", "83b36c7f6a2f466a8581d9ac7f655038",
            "83b36c7f-6a2f-466a-8581-d9ac7f655038,83b36c7f-6a2f-466a-8581-d9ac7f655039"})
    void shouldRejectMalformedIdempotencyKeysWithoutPersisting(String value) {
        Map<String, Object> data = new HashMap<>(request("FLOW-KEY", "CLI-1001", "1000", 12));
        data.put("idempotencyKey", value);
        assertError(post(data), 400, "INVALID_APPLICATION_DATA");
        assertEquals(0L, applicationCount());
    }

    @Test
    void shouldRejectMultipleIdempotencyHeaderValues() {
        assertError(client.post().uri("/applications").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString(), UUID.randomUUID().toString())
                .bodyValue(requestBody(request("FLOW-KEY", "CLI-1001", "1000", 12))).exchange(),
                400, "INVALID_APPLICATION_DATA");
        assertEquals(0L, applicationCount());
    }

    @Test
    void shouldRecoverReferenceFromAnUppercaseHeaderRetryWithoutIncreasingConsumption() {
        Map<String, Object> originalData = request("FLOW-KEY-CASE", "CLI-1001", "1000.00", 12);
        Map<String, Object> first = process(originalData, 201);
        Map<String, Object> retryData = new HashMap<>(originalData);
        retryData.put("idempotencyKey", ((String) originalData.get("idempotencyKey")).toUpperCase(java.util.Locale.ROOT));
        Map<String, Object> retried = process(retryData, 200);
        assertEquals(first.get("applicationReference"), retried.get("applicationReference"));
        assertEquals(first.get("processedAt"), retried.get("processedAt"));
        assertEquals(1L, applicationCount());
        assertEquals(0, totalApproved("CLI-1001").compareTo(new BigDecimal("1000")));
    }

    @Test
    void shouldCreateDifferentReferencesForIdenticalDataWithDifferentKeys() {
        Map<String, Object> first = process(request("FLOW-KEY-A", "CLI-1001", "1000", 12), 201);
        Map<String, Object> second = process(request("FLOW-KEY-B", "CLI-1001", "1000", 12), 201);
        assertEquals("REF-001", first.get("applicationReference"));
        assertEquals("REF-002", second.get("applicationReference"));
        assertEquals(2L, applicationCount());
        assertEquals(0, totalApproved("CLI-1001").compareTo(new BigDecimal("2000")));
    }

    @Test
    void shouldDocumentGeneratedReferenceAndRequiredIdempotencyHeader() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths['/applications'].post.parameters[0].name").isEqualTo("Idempotency-Key")
                .jsonPath("$.paths['/applications'].post.parameters[0].required").isEqualTo(true)
                .jsonPath("$.components.schemas.ApplicationRequestDto.properties.applicationReference").doesNotExist();
    }

    private String key(String label) {
        return UUID.nameUUIDFromBytes(label.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private Map<String, Object> requestBody(Map<String, Object> data) {
        Map<String, Object> body = new HashMap<>(data);
        body.remove("idempotencyKey");
        return body;
    }

    private Map<String, Object> findByKey(String label) {
        String reference = adminDatabaseClient.sql("SELECT application_reference FROM credit_applications WHERE idempotency_key = :key")
                .bind("key", key(label)).map(row -> row.get("application_reference", String.class)).one().block(TIMEOUT);
        assertNotNull(reference);
        return findByReference(reference);
    }

    private Map<String, Object> creditSummary(String customerId) {
        Map<String, Object> body = client.get().uri("/customers/{customerId}/credit-summary", customerId).exchange()
                .expectStatus().isOk().expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(RESPONSE_TYPE).returnResult().getResponseBody();
        assertNotNull(body);
        return body;
    }

    private Map<String, Object> applicationPage(int page, int size) {
        Map<String, Object> response = client.get().uri("/applications?page=" + page + "&size=" + size).exchange()
                .expectStatus().isOk().expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(RESPONSE_TYPE).returnResult().getResponseBody();
        assertNotNull(response);
        return response;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pageContent(Map<String, Object> response) {
        return (List<Map<String, Object>>) response.get("content");
    }

    private void assertPageMetadata(Map<String, Object> response, int page, int size,
            long totalElements, long totalPages, boolean first, boolean last) {
        assertEquals(page, response.get("page"));
        assertEquals(size, response.get("size"));
        assertEquals(totalElements, ((Number) response.get("totalElements")).longValue());
        assertEquals(totalPages, ((Number) response.get("totalPages")).longValue());
        assertEquals(first, response.get("first"));
        assertEquals(last, response.get("last"));
    }

    private void assertDecimal(String expected, Object actual) {
        assertInstanceOf(String.class, actual);
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal((String) actual)));
    }

    private void lockCustomer(Connection connection, String customerId) {
        Mono.from(connection.beginTransaction()).then(Mono.from(connection
                        .createStatement("SELECT customer_id FROM customers WHERE customer_id = $1 FOR UPDATE")
                        .bind(0, customerId).execute())
                .flatMapMany(result -> result.map((row, metadata) -> row.get("customer_id", String.class)))
                .then()).block(TIMEOUT);
    }

    private WebTestClient.ResponseSpec post(Map<String, Object> data) {
        var request = client.post().uri("/applications").contentType(MediaType.APPLICATION_JSON);
        if (data.containsKey("idempotencyKey")) {
            request.header("Idempotency-Key", (String) data.get("idempotencyKey"));
        }
        return request.bodyValue(requestBody(data)).exchange();
    }

    private Map<String, Object> process(Map<String, Object> data, int status) {
        Map<String, Object> body = post(data).expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(RESPONSE_TYPE).returnResult().getResponseBody();
        assertNotNull(body);
        return body;
    }

    private Map<String, Object> findByReference(String reference) {
        Map<String, Object> body = client.get().uri("/applications/{reference}", reference).exchange()
                .expectStatus().isOk().expectBody(RESPONSE_TYPE).returnResult().getResponseBody();
        assertNotNull(body);
        return body;
    }

    private Map<String, Object> assertError(WebTestClient.ResponseSpec response, int status, String code) {
        EntityExchangeResult<Map<String, Object>> result = response.expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(RESPONSE_TYPE).returnResult();
        Map<String, Object> error = result.getResponseBody();
        assertNotNull(error);
        assertEquals(code, error.get("code"));
        assertNotNull(error.get("message"));
        String traceId = (String) error.get("traceId");
        assertNotNull(UUID.fromString(traceId));
        assertEquals(traceId, result.getResponseHeaders().getFirst("X-Trace-Id"));
        return error;
    }

    private Mono<ResponseEntity<Map<String, Object>>> send(Map<String, Object> request) {
        return concurrentClient.post().uri("/applications").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", (String) request.get("idempotencyKey"))
                .bodyValue(requestBody(request)).exchangeToMono(response -> response.toEntity(RESPONSE_TYPE));
    }

    private long applicationCount() {
        Long count = adminDatabaseClient.sql("SELECT COUNT(*) AS total FROM credit_applications")
                .map(row -> row.get("total", Long.class)).one().block(TIMEOUT);
        assertNotNull(count);
        return count;
    }

    private BigDecimal totalApproved(String customerId) {
        BigDecimal total = adminDatabaseClient.sql("""
                        SELECT COALESCE(SUM(amount), 0) AS total
                        FROM credit_applications WHERE customer_id = :customerId AND status = 'APPROVED'
                        """)
                .bind("customerId", customerId).map(row -> row.get("total", BigDecimal.class)).one().block(TIMEOUT);
        assertNotNull(total);
        return total;
    }

    @SuppressWarnings("unchecked")
    private void synchronizeInitialKeyLookups(String reference) {
        AtomicInteger emptyLookups = new AtomicInteger();
        Sinks.Empty<Void> bothLookupsCompleted = Sinks.empty();
        doAnswer(invocation -> {
            Mono<CreditApplication> originalLookup = (Mono<CreditApplication>) invocation.callRealMethod();
            return originalLookup.switchIfEmpty(Mono.defer(() -> {
                if (emptyLookups.incrementAndGet() == 2) {
                    bothLookupsCompleted.tryEmitEmpty();
                }
                return bothLookupsCompleted.asMono().then(Mono.empty());
            }));
        }).when(applicationAdapter).findByIdempotencyKey(key(reference));
    }
}
