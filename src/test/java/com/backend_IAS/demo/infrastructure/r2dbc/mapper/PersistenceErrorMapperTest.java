package com.backend_IAS.demo.infrastructure.r2dbc.mapper;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.backend_IAS.demo.exception.database.DuplicateIdempotencyKeyException;
import com.backend_IAS.demo.exception.database.PersistenceFailureException;
import com.backend_IAS.demo.exception.database.PersistenceTimeoutException;
import io.r2dbc.postgresql.api.ErrorDetails;
import io.r2dbc.postgresql.api.PostgresqlException;
import io.r2dbc.spi.R2dbcException;
import io.netty.channel.ConnectTimeoutException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

class PersistenceErrorMapperTest {

    @Test
    void shouldTranslateNestedIdempotencyConstraintViolationAndPreserveOriginalCause() {
        Throwable postgresFailure = postgresFailure("23505", "uq_credit_applications_idempotency_key");
        Throwable original = new DataAccessResourceFailureException("Insert failed", postgresFailure);

        Throwable translated = PersistenceErrorMapper.mapInsertFailure(original);

        assertInstanceOf(DuplicateIdempotencyKeyException.class, translated);
        assertSame(original, translated.getCause());
    }

    @ParameterizedTest
    @CsvSource({
            "23505, another_unique_constraint",
            "23505, uq_credit_applications_reference",
            "23505,",
            "23503, uq_credit_applications_reference"
    })
    void shouldNotMisclassifyOtherDatabaseFailuresAsDuplicateKeys(String state, String constraint) {
        Throwable original = postgresFailure(state, constraint);

        Throwable translated = PersistenceErrorMapper.mapInsertFailure(original);

        assertInstanceOf(PersistenceFailureException.class, translated);
        assertSame(original, translated.getCause());
    }

    @Test
    void shouldTranslateDatabaseReadFailuresWithoutUsingInsertDuplicateRecovery() {
        Throwable original = postgresFailure("23505", "uq_credit_applications_reference");

        Throwable translated = PersistenceErrorMapper.mapFailure(original);

        assertInstanceOf(PersistenceFailureException.class, translated);
        assertSame(original, translated.getCause());
    }

    @Test
    void shouldTranslateNestedSpringDataFailure() {
        Throwable original = new IllegalStateException("Query failed",
                new DataAccessResourceFailureException("Connection unavailable"));

        Throwable translated = PersistenceErrorMapper.mapFailure(original);

        assertInstanceOf(PersistenceFailureException.class, translated);
        assertSame(original, translated.getCause());
    }

    @Test
    void shouldTranslateTransactionFailure() {
        Throwable original = new CannotCreateTransactionException("Transaction unavailable");

        Throwable translated = PersistenceErrorMapper.mapFailure(original);

        assertInstanceOf(PersistenceFailureException.class, translated);
        assertSame(original, translated.getCause());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldNotWrapAlreadyTranslatedFailuresAgain(boolean duplicate) {
        Throwable cause = new IllegalStateException("Original failure");
        Throwable translated = duplicate ? new DuplicateIdempotencyKeyException(cause) : new PersistenceFailureException(cause);

        assertSame(translated, PersistenceErrorMapper.mapFailure(translated));
        assertSame(translated, PersistenceErrorMapper.mapInsertFailure(translated));
        assertSame(cause, translated.getCause());
    }

    @Test
    void shouldPropagateNonDatabaseErrorsWithoutReclassifyingThem() {
        Throwable original = new IllegalArgumentException("Invalid mapping input");

        assertSame(original, PersistenceErrorMapper.mapFailure(original));
        assertSame(original, PersistenceErrorMapper.mapInsertFailure(original));
    }

    @ParameterizedTest
    @ValueSource(strings = {"55P03", "57014"})
    void shouldRecognizeLockAndStatementTimeoutsThroughSpringWrappers(String sqlState) {
        Throwable original = new DataAccessResourceFailureException("Timed out", postgresFailure(sqlState, null));
        Throwable translated = PersistenceErrorMapper.mapFailure(original);
        assertInstanceOf(PersistenceTimeoutException.class, translated);
        assertSame(original, translated.getCause());
        assertSame(translated, PersistenceErrorMapper.mapInsertFailure(translated));
    }

    @Test
    void shouldRecognizePoolTimeoutThroughTransactionCreationFailure() {
        Throwable original = new CannotCreateTransactionException("Pool exhausted", new TimeoutException("Acquire timed out"));
        Throwable translated = PersistenceErrorMapper.mapFailure(original);
        assertInstanceOf(PersistenceTimeoutException.class, translated);
        assertSame(original, translated.getCause());
    }

    @Test
    void shouldRecognizeDriverConnectionTimeout() {
        Throwable original = new DataAccessResourceFailureException("Connection failed",
                new ConnectTimeoutException("Connection timed out"));
        Throwable translated = PersistenceErrorMapper.mapFailure(original);
        assertInstanceOf(PersistenceTimeoutException.class, translated);
        assertSame(original, translated.getCause());
    }

    private Throwable postgresFailure(String state, String constraint) {
        ErrorDetails details = mock(ErrorDetails.class);
        when(details.getCode()).thenReturn(state);
        when(details.getConstraintName()).thenReturn(Optional.ofNullable(constraint));
        return new PostgresFailure(state, details);
    }

    private static final class PostgresFailure extends R2dbcException implements PostgresqlException {

        private final ErrorDetails details;

        private PostgresFailure(String state, ErrorDetails details) {
            super("Database operation failed", state, 0);
            this.details = details;
        }

        @Override
        public ErrorDetails getErrorDetails() {
            return details;
        }
    }
}
