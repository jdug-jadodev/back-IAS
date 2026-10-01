package com.backend_IAS.demo.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import com.backend_IAS.demo.domain.enums.RejectionReason;
import org.junit.jupiter.api.Test;

class CreditApplicationTest {

    private static final Instant PROCESSED_AT = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    void shouldBuildApprovedApplication() {
        ApplicationData data = applicationData();

        CreditApplication application = CreditApplication.approved(data, PROCESSED_AT);

        assertSame(data, application.getData());
        assertTrue(application.getDecision().isApproved());
        assertNull(application.getDecision().getReason());
        assertEquals(PROCESSED_AT, application.getProcessedAt());
    }

    @Test
    void shouldBuildRejectedApplication() {
        ApplicationData data = applicationData();

        CreditApplication application = CreditApplication.rejected(
                data,
                RejectionReason.INSUFFICIENT_LIMIT,
                PROCESSED_AT
        );

        assertTrue(application.getDecision().isRejected());
        assertEquals(RejectionReason.INSUFFICIENT_LIMIT, application.getDecision().getReason());
    }

    @Test
    void shouldDistinguishCreatedApplicationFromExistingApplication() {
        CreditApplication application = CreditApplication.approved(applicationData(), PROCESSED_AT);

        ProcessingResult createdResult = ProcessingResult.created(application);
        ProcessingResult existingResult = ProcessingResult.existing(application);

        assertTrue(createdResult.isCreated());
        assertFalse(existingResult.isCreated());
        assertSame(application, createdResult.getApplication());
        assertSame(application, existingResult.getApplication());
    }

    private ApplicationData applicationData() {
        return new ApplicationData("REF-001", "CLI-1001", new BigDecimal("1000000"), 12);
    }
}
