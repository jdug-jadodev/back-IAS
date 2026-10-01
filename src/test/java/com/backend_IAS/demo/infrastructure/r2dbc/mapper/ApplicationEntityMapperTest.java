package com.backend_IAS.demo.infrastructure.r2dbc.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import com.backend_IAS.demo.infrastructure.r2dbc.entity.ApplicationEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ApplicationEntityMapperTest {

    @Test
    void shouldReadUnknownCustomerAndHistoricalReasonWithoutInventingCustomerLink() {
        ApplicationEntity entity = ApplicationEntity.builder()
                .id(42L)
                .applicationReference("REF-UNKNOWN")
                .idempotencyKey("83b36c7f-6a2f-466a-8581-d9ac7f655038")
                .requestedCustomerId("CLI-MISSING")
                .amount(new BigDecimal("1000.123456789123456789"))
                .termMonths(12)
                .status("REJECTED")
                .reasonCode("CUSTOMER_NOT_FOUND")
                .reason("El cliente no estaba registrado al procesar la solicitud")
                .processedAt(OffsetDateTime.parse("2026-10-01T07:00:00.123456-05:00"))
                .build();

        CreditApplication application = ApplicationEntityMapper.toDomain(entity);

        assertEquals("REF-UNKNOWN", application.getData().getApplicationReference());
        assertEquals(entity.getIdempotencyKey(), application.getData().getIdempotencyKey());
        assertEquals("CLI-MISSING", application.getData().getCustomerId());
        assertEquals(new BigDecimal("1000.123456789123456789"), application.getData().getAmount());
        assertEquals(12, application.getData().getTermMonths());
        assertNull(application.getIdentifiedCustomerId());
        assertEquals(ApplicationStatus.REJECTED, application.getDecision().getStatus());
        assertEquals(RejectionReason.CUSTOMER_NOT_FOUND, application.getDecision().getReason());
        assertEquals("El cliente no estaba registrado al procesar la solicitud",
                application.getDecision().getReasonDescription());
        assertEquals(Instant.parse("2026-10-01T12:00:00.123456Z"), application.getProcessedAt());

        ApplicationEntity remapped = ApplicationEntityMapper.toEntity(application);
        assertEquals(entity.getIdempotencyKey(), remapped.getIdempotencyKey());
        assertEquals(entity.getRequestedCustomerId(), remapped.getRequestedCustomerId());
        assertNull(remapped.getIdentifiedCustomerId());
        assertEquals(entity.getReason(), remapped.getReason());
        assertEquals(entity.getProcessedAt().toInstant(), remapped.getProcessedAt().toInstant());
        assertEquals(ZoneOffset.UTC, remapped.getProcessedAt().getOffset());
        assertNull(remapped.getId());
    }

    @Test
    void shouldKeepDatabaseGeneratedFieldsUnsetBeforeInsertion() {
        CreditApplication application = approvedApplication().toBuilder().processedAt(null).build();

        ApplicationEntity entity = ApplicationEntityMapper.toEntity(application);

        assertNull(entity.getId());
        assertNull(entity.getProcessedAt());
        assertEquals("REF-APPROVED", entity.getApplicationReference());
        assertEquals("CLI-1001", entity.getRequestedCustomerId());
        assertEquals("CLI-1001", entity.getIdentifiedCustomerId());
        assertEquals(new BigDecimal("1000000.00"), entity.getAmount());
    }

    @Test
    void shouldPreserveApprovalWithNoRejectionReasonAndUtcTimestamp() {
        CreditApplication original = approvedApplication();

        ApplicationEntity entity = ApplicationEntityMapper.toEntity(original);

        assertEquals("APPROVED", entity.getStatus());
        assertNull(entity.getReasonCode());
        assertNull(entity.getReason());
        assertEquals(OffsetDateTime.parse("2026-10-01T12:00:00Z"), entity.getProcessedAt());
        assertEquals(original, ApplicationEntityMapper.toDomain(entity));
    }

    @ParameterizedTest
    @CsvSource({
            "El cupo estaba agotado al evaluar, El cupo estaba agotado al evaluar",
            ", El cupo disponible es insuficiente"
    })
    void shouldPreferHistoricalReasonAndFallBackOnlyWhenItIsMissing(String description, String expected) {
        CreditApplication application = approvedApplication().toBuilder()
                .decision(CreditDecision.builder()
                        .status(ApplicationStatus.REJECTED)
                        .reason(RejectionReason.INSUFFICIENT_LIMIT)
                        .reasonDescription(description)
                        .build())
                .build();

        ApplicationEntity entity = ApplicationEntityMapper.toEntity(application);

        assertEquals("REJECTED", entity.getStatus());
        assertEquals("INSUFFICIENT_LIMIT", entity.getReasonCode());
        assertEquals(expected, entity.getReason());
    }

    @ParameterizedTest
    @CsvSource({"UNKNOWN, INSUFFICIENT_LIMIT", "REJECTED, UNKNOWN"})
    void shouldRejectUnknownPersistedStatusOrReasonInsteadOfInventingADecision(String status, String reason) {
        ApplicationEntity entity = ApplicationEntityMapper.toEntity(approvedApplication()).toBuilder()
                .status(status)
                .reasonCode(reason)
                .build();

        assertThrows(IllegalArgumentException.class, () -> ApplicationEntityMapper.toDomain(entity));
    }

    private CreditApplication approvedApplication() {
        return CreditApplication.builder()
                .data(ApplicationData.builder()
                        .applicationReference("REF-APPROVED")
                        .customerId("CLI-1001")
                        .amount(new BigDecimal("1000000.00"))
                        .termMonths(12)
                        .build())
                .decision(CreditDecision.builder().status(ApplicationStatus.APPROVED).build())
                .identifiedCustomerId("CLI-1001")
                .processedAt(Instant.parse("2026-10-01T12:00:00Z"))
                .build();
    }
}
