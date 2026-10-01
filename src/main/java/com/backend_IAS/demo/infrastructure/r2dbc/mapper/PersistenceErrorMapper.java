package com.backend_IAS.demo.infrastructure.r2dbc.mapper;

import com.backend_IAS.demo.exception.database.DuplicateIdempotencyKeyException;
import com.backend_IAS.demo.exception.database.PersistenceFailureException;
import com.backend_IAS.demo.exception.database.PersistenceTimeoutException;
import io.r2dbc.postgresql.api.PostgresqlException;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.R2dbcTimeoutException;
import io.netty.channel.ConnectTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

public final class PersistenceErrorMapper {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String IDEMPOTENCY_CONSTRAINT = "uq_credit_applications_idempotency_key";
    private static final String LOCK_NOT_AVAILABLE_SQL_STATE = "55P03";
    private static final String QUERY_CANCELED_SQL_STATE = "57014";

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
                            .filter(IDEMPOTENCY_CONSTRAINT::equals).isPresent()) {
                return new DuplicateIdempotencyKeyException(error);
            }
        }
        return mapFailure(error);
    }

    public static Throwable mapFailure(Throwable error) {
        if (isTranslated(error)) {
            return error;
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof R2dbcTimeoutException || cause instanceof TimeoutException
                    || cause instanceof ConnectTimeoutException
                    || (cause instanceof R2dbcException r2dbcError
                    && (LOCK_NOT_AVAILABLE_SQL_STATE.equals(r2dbcError.getSqlState())
                    || QUERY_CANCELED_SQL_STATE.equals(r2dbcError.getSqlState())))) {
                return new PersistenceTimeoutException(error);
            }
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
        return error instanceof DuplicateIdempotencyKeyException || error instanceof PersistenceFailureException
                || error instanceof PersistenceTimeoutException;
    }
}
