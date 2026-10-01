package com.backend_IAS.demo.infrastructure.r2dbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.backend_IAS.demo.application.mapper.ApplicationDtoMapper;
import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.entity.Customer;
import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.enums.CustomerStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import com.backend_IAS.demo.domain.port.portout.ApplicationPort;
import com.backend_IAS.demo.domain.port.portout.CustomerPort;
import com.backend_IAS.demo.domain.port.portout.TransactionPort;
import com.backend_IAS.demo.exception.application.InvalidApplicationDataException;
import com.backend_IAS.demo.exception.database.DuplicateReferenceException;
import com.backend_IAS.demo.exception.database.PersistenceFailureException;
import io.r2dbc.spi.ConnectionFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class R2dbcAdaptersIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("credit_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + POSTGRES.getHost()
                + ":" + POSTGRES.getMappedPort(5432) + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    @Autowired
    private ApplicationPort applicationPort;

    @Autowired
    private CustomerPort customerPort;

    @Autowired
    private TransactionPort transactionPort;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void initializeDatabase() {
        new ResourceDatabasePopulator(new ClassPathResource("persistence-test-schema.sql"))
                .populate(connectionFactory)
                .block(TIMEOUT);
    }

    @Test
    void shouldMapKnownCustomerAndReturnEmptyForUnknownCustomer() {
        StepVerifier.create(transactionPort.execute(() -> customerPort.findWithLock("CLI-1001")))
                .assertNext(customer -> {
                    assertEquals("CLI-1001", customer.getCustomerId());
                    assertEquals(CustomerStatus.ELIGIBLE, customer.getStatus());
                    assertEquals(0, new BigDecimal("10000000").compareTo(customer.getCreditLimit()));
                })
                .verifyComplete();

        StepVerifier.create(transactionPort.execute(() -> customerPort.findWithLock("CLI-MISSING")))
                .verifyComplete();
    }

    @Test
    void shouldInsertWithoutRoundingAndReturnDatabaseTimestamp() {
        CreditApplication requested = approved("REF-001", "CLI-1001", "1000000.123456789123456789")
                .toBuilder().processedAt(Instant.parse("2000-01-01T00:00:00Z")).build();

        CreditApplication saved = applicationPort.insert(requested).block(TIMEOUT);
        assertNotNull(saved);
        assertNotNull(saved.getProcessedAt());
        assertNotEquals(requested.getProcessedAt(), saved.getProcessedAt());
        assertEquals("CLI-1001", saved.getIdentifiedCustomerId());
        assertEquals(0, requested.getData().getAmount().compareTo(saved.getData().getAmount()));

        StepVerifier.create(applicationPort.findByReference("REF-001"))
                .expectNext(saved)
                .verifyComplete();
        StepVerifier.create(applicationPort.findByReference("REF-MISSING")).verifyComplete();
    }

    @Test
    void shouldPreserveUnknownCustomerAndHistoricalRejectionMessage() {
        String historicalMessage = "El cliente no estaba registrado al procesar la solicitud";
        CreditApplication requested = rejected("REF-UNKNOWN", "CLI-MISSING", "1000",
                RejectionReason.CUSTOMER_NOT_FOUND).toBuilder()
                .decision(CreditDecision.builder()
                        .status(ApplicationStatus.REJECTED)
                        .reason(RejectionReason.CUSTOMER_NOT_FOUND)
                        .reasonDescription(historicalMessage)
                        .build())
                .build();

        applicationPort.insert(requested).block(TIMEOUT);

        StepVerifier.create(applicationPort.findByReference("REF-UNKNOWN"))
                .assertNext(application -> {
                    assertEquals("CLI-MISSING", application.getData().getCustomerId());
                    assertNull(application.getIdentifiedCustomerId());
                    assertEquals(historicalMessage, application.getDecision().getReasonDescription());
                    assertEquals(historicalMessage, ApplicationDtoMapper.toResponse(application).getReason());
                })
                .verifyComplete();
    }

    @Test
    void shouldCountOnlyApprovalsAndReturnZeroWhenThereAreNone() {
        applicationPort.insert(approved("REF-001", "CLI-1001", "1000000.123456789123456789"))
                .then(applicationPort.insert(approved("REF-002", "CLI-1001", "2.000000000000000001")))
                .then(applicationPort.insert(rejected("REF-003", "CLI-1001", "-7", RejectionReason.INVALID_AMOUNT)))
                .block(TIMEOUT);

        StepVerifier.create(applicationPort.getTotalApproved("CLI-1001"))
                .expectNextMatches(total -> total.compareTo(new BigDecimal("1000002.123456789123456790")) == 0)
                .verifyComplete();
        StepVerifier.create(applicationPort.getTotalApproved("CLI-2001"))
                .expectNextMatches(total -> total.signum() == 0)
                .verifyComplete();
    }

    @Test
    void shouldRejectDuplicateReferenceAcrossCustomersWithoutOverwritingOriginal() {
        CreditApplication original = applicationPort.insert(approved("REF-001", "CLI-1001", "1000"))
                .block(TIMEOUT);

        StepVerifier.create(applicationPort.insert(approved("REF-001", "CLI-2001", "2000")))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof DuplicateReferenceException);
                    assertNotNull(error.getCause());
                })
                .verify();
        StepVerifier.create(applicationPort.findByReference("REF-001"))
                .expectNext(original)
                .verifyComplete();
    }

    @Test
    void shouldNotClassifyAnotherUniqueConstraintAsDuplicateReference() {
        databaseClient.sql("CREATE UNIQUE INDEX uq_test_amount ON credit_applications(amount)")
                .then().block(TIMEOUT);
        applicationPort.insert(approved("REF-001", "CLI-1001", "1000")).block(TIMEOUT);

        StepVerifier.create(applicationPort.insert(approved("REF-002", "CLI-1001", "1000")))
                .expectError(PersistenceFailureException.class)
                .verify();
    }

    @Test
    void shouldTranslateForeignKeyFailureWithoutTurningItIntoCreditRejection() {
        StepVerifier.create(applicationPort.insert(approved("REF-INVALID", "CLI-MISSING", "1000")))
                .expectError(PersistenceFailureException.class)
                .verify();
        StepVerifier.create(applicationPort.findByReference("REF-INVALID")).verifyComplete();
    }

    @Test
    void shouldListRecentApplicationsWithStableTieBreakerAndLimit() {
        for (int index = 1; index <= 3; index++) {
            applicationPort.insert(approved("REF-00" + index, "CLI-1001", "1000")).block(TIMEOUT);
        }
        databaseClient.sql("UPDATE credit_applications SET processed_at = '2026-10-01T12:00:00Z'")
                .then().block(TIMEOUT);

        StepVerifier.create(applicationPort.listRecent(2))
                .expectNextMatches(application -> application.getData().getApplicationReference().equals("REF-003"))
                .expectNextMatches(application -> application.getData().getApplicationReference().equals("REF-002"))
                .verifyComplete();
    }

    @Test
    void shouldRollbackInsertionAndPreserveApplicationError() {
        InvalidApplicationDataException failure = new InvalidApplicationDataException("Datos de prueba inválidos");

        StepVerifier.create(transactionPort.execute(() ->
                applicationPort.insert(approved("REF-ROLLBACK", "CLI-1001", "1000"))
                        .then(Mono.error(failure))))
                .expectErrorSatisfies(error -> assertSame(failure, error))
                .verify();
        StepVerifier.create(applicationPort.findByReference("REF-ROLLBACK")).verifyComplete();
    }

    @Test
    void shouldCreateOperationOnSubscriptionAndUseReadCommitted() {
        AtomicInteger invocations = new AtomicInteger();
        Mono<String> operation = transactionPort.execute(() -> {
            invocations.incrementAndGet();
            return databaseClient.sql("SHOW transaction_isolation")
                    .map(row -> row.get("transaction_isolation", String.class))
                    .one();
        });

        assertEquals(0, invocations.get());
        StepVerifier.create(operation).expectNext("read committed").verifyComplete();
        assertEquals(1, invocations.get());
    }

    @Test
    void shouldCommitBeforeDeliveringResultToAnotherConnection() {
        StepVerifier.create(transactionPort.execute(() ->
                applicationPort.insert(approved("REF-COMMITTED", "CLI-1001", "1000")))
                .flatMap(saved -> applicationPort.findByReference("REF-COMMITTED")
                        .switchIfEmpty(Mono.error(new AssertionError("Result emitted before commit")))))
                .expectNextMatches(application -> application.getData()
                        .getApplicationReference().equals("REF-COMMITTED"))
                .verifyComplete();
    }

    @Test
    void shouldEmitNoResultWhenCommitFails() {
        databaseClient.sql("""
                ALTER TABLE credit_applications DROP CONSTRAINT fk_credit_applications_customer,
                ADD CONSTRAINT fk_credit_applications_customer FOREIGN KEY (customer_id)
                    REFERENCES customers(customer_id) DEFERRABLE INITIALLY DEFERRED
                """).then().block(TIMEOUT);

        StepVerifier.create(transactionPort.execute(() ->
                applicationPort.insert(approved("REF-COMMIT-FAILURE", "CLI-MISSING", "1000"))))
                .expectError(PersistenceFailureException.class)
                .verify();
        StepVerifier.create(applicationPort.findByReference("REF-COMMIT-FAILURE")).verifyComplete();
    }

    @Test
    void shouldHoldCustomerLockUntilTransactionCompletes() throws Exception {
        Sinks.One<Customer> acquired = Sinks.one();
        Sinks.Empty<Void> release = Sinks.empty();
        CompletableFuture<Customer> first = transactionPort.execute(() ->
                customerPort.findWithLock("CLI-1001")
                        .doOnNext(customer -> acquired.tryEmitValue(customer))
                        .flatMap(customer -> release.asMono().thenReturn(customer)))
                .toFuture();

        CompletableFuture<Customer> second = null;
        try {
            assertNotNull(acquired.asMono().block(TIMEOUT));
            second = transactionPort.execute(() -> customerPort.findWithLock("CLI-1001")).toFuture();
            CompletableFuture<Customer> blockedQuery = second;
            assertThrows(TimeoutException.class, () -> blockedQuery.get(300, TimeUnit.MILLISECONDS));
            assertFalse(second.isDone());
        } finally {
            release.tryEmitEmpty();
            assertNotNull(first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            if (second != null) {
                assertNotNull(second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            }
        }
    }

    private CreditApplication approved(String reference, String customerId, String amount) {
        return CreditApplication.builder()
                .data(data(reference, customerId, amount))
                .decision(CreditDecision.builder().status(ApplicationStatus.APPROVED).build())
                .identifiedCustomerId(customerId)
                .build();
    }

    private CreditApplication rejected(String reference, String customerId, String amount, RejectionReason reason) {
        return CreditApplication.builder()
                .data(data(reference, customerId, amount))
                .decision(CreditDecision.builder().status(ApplicationStatus.REJECTED).reason(reason).build())
                .identifiedCustomerId(reason == RejectionReason.CUSTOMER_NOT_FOUND ? null : customerId)
                .build();
    }

    private ApplicationData data(String reference, String customerId, String amount) {
        return ApplicationData.builder()
                .applicationReference(reference)
                .customerId(customerId)
                .amount(new BigDecimal(amount))
                .termMonths(12)
                .build();
    }
}
