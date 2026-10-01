package com.backend_IAS.demo.domain.entity;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class ApplicationPage {
    private final List<CreditApplication> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final long totalPages;
    private final boolean first;
    private final boolean last;
}
