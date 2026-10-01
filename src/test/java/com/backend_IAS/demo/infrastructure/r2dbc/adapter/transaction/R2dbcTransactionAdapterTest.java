package com.backend_IAS.demo.infrastructure.r2dbc.adapter.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.backend_IAS.demo.exception.application.InvalidApplicationDataException;
import com.backend_IAS.demo.exception.database.DuplicateReferenceException;
import com.backend_IAS.demo.exception.database.PersistenceFailureException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class R2dbcTransactionAdapterTest {

    private final ReactiveTransactionManager transactionManager = mock(ReactiveTransactionManager.class);
    private final ReactiveTransaction transaction = mock(ReactiveTransaction.class);
    private final R2dbcTransactionAdapter adapter =
            new R2dbcTransactionAdapter(TransactionalOperator.create(transactionManager));

    @BeforeEach
    void configureTransactionManager() {
        when(transactionManager.getReactiveTransaction(any(TransactionDefinition.class))).thenReturn(Mono.just(transaction));
        when(transactionManager.commit(transaction)).thenReturn(Mono.empty());
        when(transactionManager.rollback(transaction)).thenReturn(Mono.empty());
    }

    @Test
    void shouldCreateOperationAndTransactionOnlyOnSubscription() {
        AtomicInteger invocations = new AtomicInteger();
        Mono<Integer> result = adapter.execute(() -> Mono.just(invocations.incrementAndGet()));

        assertEquals(0, invocations.get());
        verifyNoInteractions(transactionManager);
        StepVerifier.create(result).expectNext(1).verifyComplete();
        StepVerifier.create(result).expectNext(2).verifyComplete();
        assertEquals(2, invocations.get());
        verify(transactionManager, times(2)).getReactiveTransaction(any(TransactionDefinition.class));
        verify(transactionManager, times(2)).commit(transaction);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void shouldWithholdResultUntilCommitCompletes() {
        Sinks.Empty<Void> commitCompleted = Sinks.empty();
        AtomicBoolean resultEmitted = new AtomicBoolean();
        when(transactionManager.commit(transaction)).thenReturn(commitCompleted.asMono());

        Mono<String> result = adapter.execute(() -> Mono.just("saved"))
                .doOnNext(value -> resultEmitted.set(true));

        StepVerifier.create(result)
                .then(() -> {
                    verify(transactionManager).commit(transaction);
                    assertFalse(resultEmitted.get());
                    assertEquals(Sinks.EmitResult.OK, commitCompleted.tryEmitEmpty());
                })
                .expectNext("saved")
                .verifyComplete();
        assertTrue(resultEmitted.get());
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void shouldPublishNoResultWhenCommitFails() {
        TransactionSystemException failure = new TransactionSystemException("Commit failed");
        when(transactionManager.commit(transaction)).thenReturn(Mono.error(failure));

        StepVerifier.create(adapter.execute(() -> Mono.just("saved")))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(PersistenceFailureException.class, error);
                    assertTrue(hasCause(error, failure));
                })
                .verify();
        verify(transactionManager).commit(transaction);
    }

    @Test
    void shouldCompleteRollbackBeforePropagatingApplicationFailure() {
        InvalidApplicationDataException failure = new InvalidApplicationDataException("Datos de prueba inválidos");
        Sinks.Empty<Void> rollbackCompleted = Sinks.empty();
        AtomicBoolean errorEmitted = new AtomicBoolean();
        when(transactionManager.rollback(transaction)).thenReturn(rollbackCompleted.asMono());

        Mono<String> result = adapter.<String>execute(() -> Mono.error(failure))
                .doOnError(error -> errorEmitted.set(true));

        StepVerifier.create(result)
                .then(() -> {
                    verify(transactionManager).rollback(transaction);
                    assertFalse(errorEmitted.get());
                    assertEquals(Sinks.EmitResult.OK, rollbackCompleted.tryEmitEmpty());
                })
                .expectErrorMatches(error -> error == failure)
                .verify();
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void shouldRollbackWhenOperationSupplierThrowsSynchronously() {
        IllegalStateException failure = new IllegalStateException("Operation creation failed");
        Mono<String> result = adapter.execute(() -> {
            throw failure;
        });

        verifyNoInteractions(transactionManager);
        StepVerifier.create(result).expectErrorMatches(error -> error == failure).verify();
        verify(transactionManager).rollback(transaction);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void shouldCommitAnEmptyOperationWithoutInventingAResult() {
        StepVerifier.create(adapter.execute(Mono::empty)).verifyComplete();

        verify(transactionManager).commit(transaction);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void shouldNotInvokeOperationWhenTransactionCannotBeCreated() {
        CannotCreateTransactionException failure = new CannotCreateTransactionException("Connection unavailable");
        AtomicInteger invocations = new AtomicInteger();
        when(transactionManager.getReactiveTransaction(any(TransactionDefinition.class))).thenReturn(Mono.error(failure));

        StepVerifier.create(adapter.execute(() -> Mono.just(invocations.incrementAndGet())))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(PersistenceFailureException.class, error);
                    assertSame(failure, error.getCause());
                })
                .verify();
        assertEquals(0, invocations.get());
        verify(transactionManager, never()).commit(any());
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void shouldPreserveTranslatedDuplicateAfterRollback() {
        DuplicateReferenceException failure = new DuplicateReferenceException(new IllegalStateException("Duplicate"));

        StepVerifier.create(adapter.execute(() -> Mono.error(failure)))
                .expectErrorMatches(error -> error == failure)
                .verify();
        verify(transactionManager).rollback(transaction);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void shouldRollbackWhenSubscriptionIsCancelled() {
        StepVerifier.create(adapter.execute(Mono::never))
                .then(() -> verify(transactionManager).getReactiveTransaction(any(TransactionDefinition.class)))
                .thenCancel()
                .verify();

        verify(transactionManager).rollback(transaction);
        verify(transactionManager, never()).commit(any());
    }

    private boolean hasCause(Throwable error, Throwable expected) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause == expected) {
                return true;
            }
        }
        return false;
    }
}
