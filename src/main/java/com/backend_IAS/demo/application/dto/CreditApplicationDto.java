package com.backend_IAS.demo.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class CreditApplicationDto {
    @JsonProperty("data")
    private final ApplicationDataDto data;

    @JsonProperty("decision")
    private final CreditDecisionDto decision;

    @JsonProperty("processedAt")
    private final Instant processedAt;

    @JsonProperty("identifiedCustomerId")
    private final String identifiedCustomerId;
}
