package com.backend_IAS.demo.infrastructure.r2dbc.mapper;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.backend_IAS.demo.exception.database.DuplicateReferenceException;
import com.backend_IAS.demo.exception.database.PersistenceFailureException;
import io.r2dbc.postgresql.api.ErrorDetails;
import io.r2dbc.postgresql.api.PostgresqlException;
import io.r2dbc.spi.R2dbcException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

class PersistenceErrorMapperTest {

    @Test
    void shouldTranslateNestedReferenceConstraintViolationAndPreserveOriginalCause() {
        Throwable postgresFailure = postgresFailure("23505", "uq_credit_applications_reference");
        Throwable original = new DataAccessResourceFailureException("Insert failed", postgresFailure);

        Throwable translated = PersistenceErrorMapper.mapInsertFailure(original);

        assertInstanceOf(DuplicateReferenceException.class, translated);
        assertSame(original, translated.getCause());
    }

    @ParameterizedTest
    @CsvSource({
            "23505, another_unique_constraint",
            "23505,",
            "23503, uq_credit_applications_reference"
    })
    void shouldNotMisclassifyOtherDatabaseFailuresAsDuplicateReferences(String state, String constraint) {
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
        Throwable translated = duplicate ? new DuplicateReferenceException(cause) : new PersistenceFailureException(cause);

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
