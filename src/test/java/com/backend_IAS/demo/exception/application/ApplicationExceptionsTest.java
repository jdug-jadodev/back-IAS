package com.backend_IAS.demo.exception.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationExceptionsTest {

    @ParameterizedTest
    @ValueSource(strings = {"REF-001", " REF-002 ", "REF-%s-%n-003"})
    void shouldIncludeApplicationReferenceLiterallyInNotFoundMessage(String reference) {
        ApplicationNotFoundException exception = new ApplicationNotFoundException(reference);

        assertEquals("No se encontró la solicitud con referencia: " + reference, exception.getMessage());
        assertNull(exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLI-1001", " CLI-2001 ", "CLI-%s-%n-003"})
    void shouldIncludeCustomerIdentifierLiterallyInNotFoundMessage(String customerId) {
        CustomerNotFoundException exception = new CustomerNotFoundException(customerId);

        assertEquals("No se encontró el cliente con identificador: " + customerId, exception.getMessage());
        assertNull(exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"REF-001", " REF-002 ", "REF-%s-%n-003"})
    void shouldIncludeConflictingReferenceWithoutInterpretingFormatCharacters(String reference) {
        IdempotencyConflictException exception = new IdempotencyConflictException(reference);

        assertEquals("La clave de idempotencia " + reference + " ya está asociada a una solicitud con datos diferentes",
                exception.getMessage());
        assertNull(exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "El monto de la solicitud es obligatorio",
            "El límite debe estar entre 1 y 100",
            "La referencia REF-%s-%n contiene datos inválidos"
    })
    void shouldPreserveValidationDetailSuppliedByTheCaller(String message) {
        InvalidApplicationDataException exception = new InvalidApplicationDataException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }
}
