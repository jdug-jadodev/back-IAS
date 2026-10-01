package com.backend_IAS.demo.infrastructure.r2dbc.repository;

import com.backend_IAS.demo.infrastructure.r2dbc.entity.ApplicationEntity;
import java.math.BigDecimal;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ApplicationR2dbcRepository extends R2dbcRepository<ApplicationEntity, Long> {

    Mono<ApplicationEntity> findByApplicationReference(String applicationReference);

    @Query("""
            SELECT COALESCE(SUM(amount), 0)
            FROM credit_applications
            WHERE customer_id = :customerId AND status = 'APPROVED'
            """)
    Mono<BigDecimal> getTotalApproved(@Param("customerId") String customerId);

    @Query("""
            INSERT INTO credit_applications (
                application_reference, requested_customer_id, customer_id,
                amount, term_months, status, reason_code, reason
            ) VALUES (
                :#{#application.applicationReference}, :#{#application.requestedCustomerId},
                :#{#application.identifiedCustomerId}, :#{#application.amount},
                :#{#application.termMonths}, :#{#application.status},
                :#{#application.reasonCode}, :#{#application.reason}
            )
            RETURNING id, application_reference, requested_customer_id, customer_id,
                      amount, term_months, status, reason_code, reason, processed_at
            """)
    Mono<ApplicationEntity> insert(@Param("application") ApplicationEntity application);

    @Query("""
            SELECT id, application_reference, requested_customer_id, customer_id,
                   amount, term_months, status, reason_code, reason, processed_at
            FROM credit_applications
            ORDER BY processed_at DESC, id DESC
            LIMIT :limit
            """)
    Flux<ApplicationEntity> listRecent(@Param("limit") int limit);
}
