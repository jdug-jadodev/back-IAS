package com.backend_IAS.demo.infrastructure.r2dbc.mapper;

import com.backend_IAS.demo.exception.database.DuplicateReferenceException;
import com.backend_IAS.demo.exception.database.PersistenceFailureException;
import io.r2dbc.postgresql.api.PostgresqlException;
import io.r2dbc.spi.R2dbcException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

public final class PersistenceErrorMapper {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String REFERENCE_CONSTRAINT = "uq_credit_applications_reference";

    private PersistenceErrorMapper() {
    }

    public static Throwable mapInsertFailure(Throwable error) {
        if (isTranslated(error)) {
            return error;
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof PostgresqlException postgresError
                    && UNIQUE_VIOLATION_SQL_STATE.equals(postgresError.getErrorDetails().getCode())
                    && postgresError.getErrorDetails().getConstraintName()
                            .filter(REFERENCE_CONSTRAINT::equals).isPresent()) {
                return new DuplicateReferenceException(error);
            }
        }
        return mapFailure(error);
    }

    public static Throwable mapFailure(Throwable error) {
        if (isTranslated(error)) {
            return error;
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataAccessException
                    || cause instanceof R2dbcException
                    || cause instanceof TransactionException) {
                return new PersistenceFailureException(error);
            }
        }
        return error;
    }

    private static boolean isTranslated(Throwable error) {
        return error instanceof DuplicateReferenceException || error instanceof PersistenceFailureException;
    }
}
