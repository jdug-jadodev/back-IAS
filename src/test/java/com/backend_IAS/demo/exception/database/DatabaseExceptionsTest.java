package com.backend_IAS.demo.exception.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.sql.SQLException;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DatabaseExceptionsTest {

    @ParameterizedTest
    @MethodSource("exceptionTypes")
    void shouldUseStableMessageAndPreserveOriginalDatabaseCause(
            Function<Throwable, RuntimeException> factory, String expectedMessage) {
        SQLException cause = new SQLException("Database constraint failure", "23505");

        RuntimeException exception = factory.apply(cause);

        assertEquals(expectedMessage, exception.getMessage());
        assertSame(cause, exception.getCause());
        assertEquals("23505", ((SQLException) exception.getCause()).getSQLState());
    }

    @ParameterizedTest
    @MethodSource("exceptionTypes")
    void shouldKeepNestedCauseChainIntactWithoutReplacingItWithTheRootCause(
            Function<Throwable, RuntimeException> factory, String expectedMessage) {
        IOException rootCause = new IOException("Connection interrupted");
        IllegalStateException original = new IllegalStateException("Operation failed", rootCause);

        RuntimeException exception = factory.apply(original);

        assertEquals(expectedMessage, exception.getMessage());
        assertSame(original, exception.getCause());
        assertSame(rootCause, exception.getCause().getCause());
    }

    private static Stream<Arguments> exceptionTypes() {
        return Stream.of(
                Arguments.of((Function<Throwable, RuntimeException>) DuplicateIdempotencyKeyException::new,
                        "Ya existe una solicitud con la clave de idempotencia indicada"),
                Arguments.of((Function<Throwable, RuntimeException>) PersistenceFailureException::new,
                        "No fue posible completar la operación de base de datos"));
    }
}
