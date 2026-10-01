package com.backend_IAS.demo.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class ProcessingResultDto {
    @JsonProperty("application")
    private final CreditApplicationDto application;

    @JsonProperty("created")
    private final boolean created;
}
