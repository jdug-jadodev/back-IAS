package com.backend_IAS.demo.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class ApplicationPageDto {
    @JsonProperty("content")
    private final List<CreditApplicationDto> content;
    @JsonProperty("page")
    private final int page;
    @JsonProperty("size")
    private final int size;
    @JsonProperty("totalElements")
    private final long totalElements;
    @JsonProperty("totalPages")
    private final long totalPages;
    @JsonProperty("first")
    private final boolean first;
    @JsonProperty("last")
    private final boolean last;
}
