package com.backend_IAS.demo.domain.entity;

import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import java.util.Objects;
import lombok.*;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class CreditDecision {
    private final ApplicationStatus status;
    private final RejectionReason reason;
    private final String reasonDescription;

    public static CreditDecision approved() {
        return CreditDecision.builder()
                .status(ApplicationStatus.APPROVED)
                .build();
    }

    public static CreditDecision rejected(RejectionReason reason) {
        Objects.requireNonNull(reason, "reason is required");
        return CreditDecision.builder()
                .status(ApplicationStatus.REJECTED)
                .reason(reason)
                .reasonDescription(reason.getDescription())
                .build();
    }

    public boolean isApproved() {
        return status == ApplicationStatus.APPROVED;
    }

    public boolean isRejected() {
        return status == ApplicationStatus.REJECTED;
    }
}
