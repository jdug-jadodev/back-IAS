package com.backend_IAS.demo.domain.factory;

import com.backend_IAS.demo.domain.entity.ApplicationPage;
import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.exception.message.DomainMessages;
import java.util.List;
import java.util.Objects;

public final class ApplicationPageFactory {
    private ApplicationPageFactory() {
    }

    public static ApplicationPage create(List<CreditApplication> content, int page, int size, long totalElements) {
        Objects.requireNonNull(content, DomainMessages.PAGE_CONTENT_REQUIRED);
        if (page < 0 || size < 1 || totalElements < 0) {
            throw new IllegalArgumentException(DomainMessages.PAGINATION_INVALID);
        }
        long totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);
        return ApplicationPage.builder()
                .content(List.copyOf(content))
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .build();
    }
}
