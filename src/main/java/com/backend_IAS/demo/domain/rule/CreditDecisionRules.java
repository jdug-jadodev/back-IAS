package com.backend_IAS.demo.domain.rule;

import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.exception.message.DomainMessages;
import java.util.Objects;

public final class CreditDecisionRules {

    private CreditDecisionRules() {
    }

    public static boolean isApproved(CreditDecision decision) {
        return Objects.requireNonNull(decision, DomainMessages.DECISION_REQUIRED).getStatus() == ApplicationStatus.APPROVED;
    }

    public static boolean isRejected(CreditDecision decision) {
        return Objects.requireNonNull(decision, DomainMessages.DECISION_REQUIRED).getStatus() == ApplicationStatus.REJECTED;
    }
}
