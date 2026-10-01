package com.backend_IAS.demo.domain.factory;

import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import com.backend_IAS.demo.exception.message.DomainMessages;
import java.util.Objects;

public final class CreditDecisionFactory {

    private CreditDecisionFactory() {
    }

    public static CreditDecision approved() {
        return CreditDecision.builder()
                .status(ApplicationStatus.APPROVED)
                .build();
    }

    public static CreditDecision rejected(RejectionReason reason) {
        Objects.requireNonNull(reason, DomainMessages.REASON_REQUIRED);
        return CreditDecision.builder()
                .status(ApplicationStatus.REJECTED)
                .reason(reason)
                .reasonDescription(reason.getDescription())
                .build();
    }
}
