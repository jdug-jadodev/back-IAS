package com.backend_IAS.demo.infrastructure.r2dbc.mapper;

import com.backend_IAS.demo.domain.entity.ApplicationPage;
import com.backend_IAS.demo.domain.factory.ApplicationPageFactory;
import com.backend_IAS.demo.exception.database.PersistenceFailureException;
import com.backend_IAS.demo.exception.message.InfrastructureMessages;
import com.backend_IAS.demo.infrastructure.r2dbc.entity.ApplicationEntity;
import com.backend_IAS.demo.infrastructure.r2dbc.entity.ApplicationPageEntity;
import java.util.List;

public final class ApplicationPageEntityMapper {
    private ApplicationPageEntityMapper() {
    }

    public static ApplicationPage toDomain(List<ApplicationPageEntity> rows, int page, int size) {
        if (rows.isEmpty()) {
            throw new PersistenceFailureException(new IllegalStateException(InfrastructureMessages.APPLICATION_PAGE_QUERY_EMPTY));
        }
        return ApplicationPageFactory.create(rows.stream()
                        .filter(row -> row.getId() != null)
                        .map(ApplicationPageEntityMapper::toApplicationEntity)
                        .map(ApplicationEntityMapper::toDomain)
                        .toList(), page, size, rows.getFirst().getTotalElements());
    }

    private static ApplicationEntity toApplicationEntity(ApplicationPageEntity row) {
        return ApplicationEntity.builder()
                .id(row.getId())
                .applicationReference(row.getApplicationReference())
                .idempotencyKey(row.getIdempotencyKey())
                .requestedCustomerId(row.getRequestedCustomerId())
                .identifiedCustomerId(row.getIdentifiedCustomerId())
                .amount(row.getAmount())
                .termMonths(row.getTermMonths())
                .status(row.getStatus())
                .reasonCode(row.getReasonCode())
                .reason(row.getReason())
                .processedAt(row.getProcessedAt())
                .build();
    }
}
