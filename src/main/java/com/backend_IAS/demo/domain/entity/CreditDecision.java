package com.backend_IAS.demo.domain.entity;

import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import lombok.*;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class CreditDecision {
    private final ApplicationStatus status;
    private final RejectionReason reason;
}
