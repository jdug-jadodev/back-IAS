package com.backend_IAS.demo.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import org.junit.jupiter.api.Test;

class CreditDecisionTest {

    @Test
    void shouldCreateApprovedDecisionWithoutReason() {
        CreditDecision decision = CreditDecision.approved();

        assertEquals(ApplicationStatus.APPROVED, decision.getStatus());
        assertNull(decision.getReason());
        assertTrue(decision.isApproved());
        assertFalse(decision.isRejected());
    }

    @Test
    void shouldCreateRejectedDecisionWithReason() {
        CreditDecision decision = CreditDecision.rejected(RejectionReason.INVALID_AMOUNT);

        assertEquals(ApplicationStatus.REJECTED, decision.getStatus());
        assertEquals(RejectionReason.INVALID_AMOUNT, decision.getReason());
        assertTrue(decision.isRejected());
        assertFalse(decision.isApproved());
    }

    @Test
    void shouldNotCreateRejectedDecisionWithoutReason() {
        assertThrows(NullPointerException.class, () -> CreditDecision.rejected(null));
    }
}
