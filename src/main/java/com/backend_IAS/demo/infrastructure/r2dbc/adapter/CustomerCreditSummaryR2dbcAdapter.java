package com.backend_IAS.demo.infrastructure.r2dbc.adapter;

import com.backend_IAS.demo.domain.entity.CustomerCreditSummary;
import com.backend_IAS.demo.domain.port.portout.CustomerCreditSummaryPort;
import com.backend_IAS.demo.infrastructure.r2dbc.mapper.CustomerCreditSummaryRowMapper;
import com.backend_IAS.demo.infrastructure.r2dbc.mapper.PersistenceErrorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class CustomerCreditSummaryR2dbcAdapter implements CustomerCreditSummaryPort {
    private final DatabaseClient databaseClient;

    @Override
    public Mono<CustomerCreditSummary> findByCustomerId(String customerId) {
        return Mono.defer(() -> databaseClient.sql("""
                        SELECT c.customer_id, c.status, c.approval_limit,
                               COALESCE(a.approved_amount, 0) AS approved_amount,
                               GREATEST(c.approval_limit - COALESCE(a.approved_amount, 0), 0) AS available_amount
                        FROM customers c
                        LEFT JOIN LATERAL (
                            SELECT SUM(amount) AS approved_amount
                            FROM credit_applications
                            WHERE customer_id = c.customer_id AND status = 'APPROVED'
                        ) a ON TRUE
                        WHERE c.customer_id = :customerId
                        """)
                .bind("customerId", customerId)
                .map((row, metadata) -> CustomerCreditSummaryRowMapper.toDomain(row)).one())
                .onErrorMap(PersistenceErrorMapper::mapFailure);
    }
}
