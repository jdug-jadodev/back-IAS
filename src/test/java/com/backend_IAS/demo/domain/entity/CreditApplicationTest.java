package com.backend_IAS.demo.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import com.backend_IAS.demo.domain.enums.RejectionReason;
import com.backend_IAS.demo.domain.factory.CreditDecisionFactory;
import com.backend_IAS.demo.domain.factory.ProcessingResultFactory;
import com.backend_IAS.demo.domain.rule.CreditDecisionRules;
import org.junit.jupiter.api.Test;

class CreditApplicationTest {

    private static final Instant PROCESSED_AT = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    void shouldBuildApprovedApplication() {
        ApplicationData data = applicationData();

        CreditApplication application = approvedApplication(data);

        assertSame(data, application.getData());
        assertTrue(CreditDecisionRules.isApproved(application.getDecision()));
        assertNull(application.getDecision().getReason());
        assertEquals(PROCESSED_AT, application.getProcessedAt());
    }

    @Test
    void shouldBuildRejectedApplication() {
        ApplicationData data = applicationData();

        CreditApplication application = CreditApplication.builder()
                .data(data)
                .decision(CreditDecisionFactory.rejected(RejectionReason.INSUFFICIENT_LIMIT))
                .processedAt(PROCESSED_AT)
                .identifiedCustomerId(data.getCustomerId())
                .build();

        assertTrue(CreditDecisionRules.isRejected(application.getDecision()));
        assertEquals(RejectionReason.INSUFFICIENT_LIMIT, application.getDecision().getReason());
    }

    @Test
    void shouldDistinguishCreatedApplicationFromExistingApplication() {
        CreditApplication application = approvedApplication(applicationData());

        ProcessingResult createdResult = ProcessingResultFactory.created(application);
        ProcessingResult existingResult = ProcessingResultFactory.existing(application);

        assertTrue(createdResult.isCreated());
        assertFalse(existingResult.isCreated());
        assertSame(application, createdResult.getApplication());
        assertSame(application, existingResult.getApplication());
    }

    private ApplicationData applicationData() {
        return new ApplicationData("REF-001", "CLI-1001", new BigDecimal("1000000"), 12);
    }

    private CreditApplication approvedApplication(ApplicationData data) {
        return CreditApplication.builder()
                .data(data)
                .decision(CreditDecisionFactory.approved())
                .processedAt(PROCESSED_AT)
                .identifiedCustomerId(data.getCustomerId())
                .build();
    }
}
