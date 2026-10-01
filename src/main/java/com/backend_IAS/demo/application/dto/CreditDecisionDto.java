package com.backend_IAS.demo.application.dto;

import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class CreditDecisionDto {
    @JsonProperty("status")
    private final ApplicationStatus status;

    @JsonProperty("reason")
    private final RejectionReason reason;

    @JsonProperty("reasonDescription")
    private final String reasonDescription;
}
