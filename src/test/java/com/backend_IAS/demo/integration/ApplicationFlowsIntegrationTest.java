package com.backend_IAS.demo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.infrastructure.r2dbc.adapter.ApplicationR2dbcAdapter;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationFlowsIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_TYPE =
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

    private DatabaseClient adminDatabaseClient;
    private WebTestClient client;
    private WebClient concurrentClient;

    @BeforeEach
    void initializeIsolatedDatabase() {
        ConnectionFactory adminConnectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
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
                .then(adminDatabaseClient.sql("GRANT USAGE, SELECT ON SEQUENCE credit_applications_id_seq TO flow_app").then())
                .block(TIMEOUT);
        String baseUrl = "http://localhost:" + serverPort;
        client = WebTestClient.bindToServer().baseUrl(baseUrl).responseTimeout(TIMEOUT).build();
        concurrentClient = WebClient.builder().baseUrl(baseUrl).build();
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
        assertEquals(response, findByReference("FLOW-APPROVED"));
        assertEquals(1L, applicationCount());
        assertEquals(0, totalApproved("CLI-1001").compareTo(new BigDecimal("1000000")));
    }

    @Test
    void shouldPreserveDecimalPrecisionThroughHttpAndPersistence() {
        String amount = "1000000.123456789123456789";

        Map<String, Object> response = process(request("FLOW-PRECISION", "CLI-1001", amount, 12), 201);

        assertEquals(amount, response.get("amount"));
        assertEquals(amount, findByReference("FLOW-PRECISION").get("amount"));
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
        assertEquals(response, findByReference("FLOW-REJECTED"));
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
        assertEquals(original, findByReference("FLOW-RETRY"));
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
        assertEquals(original, findByReference("FLOW-HISTORICAL"));
        assertEquals(1L, applicationCount());
        assertEquals(0, totalApproved(customerId).signum());
    }

    @ParameterizedTest
    @CsvSource({"CLI-2001, 1000000, 12", "CLI-1001, 1000001, 12", "CLI-1001, 1000000, 24"})
    void shouldRejectReferenceReuseWithChangedDataWithoutOverwritingOriginal(
            String customerId, String amount, int termMonths) {
        Map<String, Object> original = process(request("FLOW-CONFLICT", "CLI-1001", "1000000", 12), 201);

        assertError(post(request("FLOW-CONFLICT", customerId, amount, termMonths)), 409, "REFERENCE_CONFLICT");

        assertEquals(original, findByReference("FLOW-CONFLICT"));
        assertEquals(1L, applicationCount());
        assertEquals(0, totalApproved("CLI-1001").compareTo(new BigDecimal("1000000")));
        assertEquals(0, totalApproved("CLI-2001").signum());
    }

    @ParameterizedTest
    @ValueSource(strings = {"applicationReference", "customerId", "amount", "termMonths"})
    void shouldRejectMissingRequiredFieldsWithoutPersisting(String missingField) {
        Map<String, Object> data = new HashMap<>(request("FLOW-MISSING", "CLI-1001", "1000", 12));
        data.remove(missingField);

        assertError(post(data), 400, "INVALID_APPLICATION_DATA");

        assertEquals(0L, applicationCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"applicationReference", "customerId"})
    void shouldRejectBlankIdentifiersWithoutPersisting(String blankField) {
        Map<String, Object> data = new HashMap<>(request("FLOW-BLANK", "CLI-1001", "1000", 12));
        data.put(blankField, " \t\n ");

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
    void shouldListRecentDecisionsWithLimitAndStableTieBreaker() {
        process(request("FLOW-RECENT-1", "CLI-1001", "1000", 12), 201);
        process(request("FLOW-RECENT-2", "CLI-1002", "1000", 12), 201);
        process(request("FLOW-RECENT-3", "CLI-MISSING", "1000", 12), 201);
        adminDatabaseClient.sql("UPDATE credit_applications SET processed_at = '2026-10-01T12:00:00Z'")
                .then().block(TIMEOUT);

        List<Map<String, Object>> recent = client.get().uri("/applications?limit=2").exchange()
                .expectStatus().isOk().expectBody(LIST_TYPE).returnResult().getResponseBody();

        assertNotNull(recent);
        assertEquals(List.of("FLOW-RECENT-3", "FLOW-RECENT-2"),
                recent.stream().map(response -> response.get("applicationReference")).toList());
        assertTrue(recent.stream().allMatch(response -> response.get("status").equals("REJECTED")));
        client.get().uri("/applications").exchange().expectStatus().isOk()
                .expectBody(LIST_TYPE).value(responses -> assertEquals(3, responses.size()));
    }

    @Test
    void shouldReturnEmptyRecentListWhenNoApplicationsExist() {
        client.get().uri("/applications").exchange().expectStatus().isOk()
                .expectBody(LIST_TYPE).value(responses -> assertTrue(responses.isEmpty()));
    }

    @ParameterizedTest
    @CsvSource({"0, INVALID_APPLICATION_DATA", "101, INVALID_APPLICATION_DATA", "abc, INVALID_REQUEST"})
    void shouldRejectInvalidRecentLimits(String limit, String errorCode) {
        assertError(client.get().uri("/applications?limit=" + limit).exchange(), 400, errorCode);
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
                CHECK (application_reference <> 'FLOW-INSERT-FAILURE')
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
    void shouldProtectCreditLimitAcrossConcurrentDifferentReferences() {
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
    void shouldRecoverConcurrentIdenticalReferencesAfterRollback(String customerId) {
        synchronizeInitialReferenceLookups("FLOW-DUPLICATE");
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
        verify(applicationAdapter, times(3)).findByReference("FLOW-DUPLICATE");
        assertEquals(1L, applicationCount());
        assertEquals(0, totalApproved(customerId).compareTo(customerId.equals("CLI-1001")
                ? new BigDecimal("10000000") : BigDecimal.ZERO));
    }

    @Test
    void shouldReturnConflictForConcurrentReferenceReuseAcrossCustomers() {
        synchronizeInitialReferenceLookups("FLOW-CROSS-CUSTOMER");
        Mono<ResponseEntity<Map<String, Object>>> first = send(request("FLOW-CROSS-CUSTOMER", "CLI-1001", "1000000", 12));
        Mono<ResponseEntity<Map<String, Object>>> second = send(request("FLOW-CROSS-CUSTOMER", "CLI-2001", "1000000", 12));

        StepVerifier.create(Mono.zip(first, second))
                .assertNext(pair -> {
                    List<ResponseEntity<Map<String, Object>>> responses = List.of(pair.getT1(), pair.getT2());
                    assertEquals(1L, responses.stream().filter(response -> response.getStatusCode().value() == 201).count());
                    assertEquals(1L, responses.stream().filter(response -> response.getStatusCode().value() == 409).count());
                    ResponseEntity<Map<String, Object>> conflict = responses.stream()
                            .filter(response -> response.getStatusCode().value() == 409).findFirst().orElseThrow();
                    assertEquals("REFERENCE_CONFLICT", conflict.getBody().get("code"));
                    assertEquals(conflict.getBody().get("traceId"), conflict.getHeaders().getFirst("X-Trace-Id"));
                })
                .expectComplete().verify(TIMEOUT);

        verify(applicationAdapter, times(2)).insert(any(CreditApplication.class));
        assertEquals(1L, applicationCount());
        assertEquals(0, totalApproved("CLI-1001").add(totalApproved("CLI-2001")).compareTo(new BigDecimal("1000000")));
        assertEquals("APPROVED", findByReference("FLOW-CROSS-CUSTOMER").get("status"));
    }

    private Map<String, Object> request(String reference, String customerId, String amount, int termMonths) {
        return Map.of("applicationReference", reference, "customerId", customerId, "amount", amount, "termMonths", termMonths);
    }

    private WebTestClient.ResponseSpec post(Map<String, Object> data) {
        return client.post().uri("/applications").contentType(MediaType.APPLICATION_JSON).bodyValue(data).exchange();
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
                .bodyValue(request).exchangeToMono(response -> response.toEntity(RESPONSE_TYPE));
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
    private void synchronizeInitialReferenceLookups(String reference) {
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
        }).when(applicationAdapter).findByReference(reference);
    }
}
