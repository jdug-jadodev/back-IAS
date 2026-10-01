package com.backend_IAS.demo.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import com.backend_IAS.demo.domain.factory.CreditDecisionFactory;
import com.backend_IAS.demo.domain.rule.CreditDecisionRules;
import org.junit.jupiter.api.Test;

class CreditDecisionTest {

    @Test
    void shouldCreateApprovedDecisionWithoutReason() {
        CreditDecision decision = CreditDecisionFactory.approved();

        assertEquals(ApplicationStatus.APPROVED, decision.getStatus());
        assertNull(decision.getReason());
        assertTrue(CreditDecisionRules.isApproved(decision));
        assertFalse(CreditDecisionRules.isRejected(decision));
    }

    @Test
    void shouldCreateRejectedDecisionWithReason() {
        CreditDecision decision = CreditDecisionFactory.rejected(RejectionReason.INVALID_AMOUNT);

        assertEquals(ApplicationStatus.REJECTED, decision.getStatus());
        assertEquals(RejectionReason.INVALID_AMOUNT, decision.getReason());
        assertTrue(CreditDecisionRules.isRejected(decision));
        assertFalse(CreditDecisionRules.isApproved(decision));
    }

    @Test
    void shouldNotCreateRejectedDecisionWithoutReason() {
        assertThrows(NullPointerException.class, () -> CreditDecisionFactory.rejected(null));
    }
}
