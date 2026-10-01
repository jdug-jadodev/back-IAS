package com.backend_IAS.demo.infrastructure.r2dbc.repository;

import com.backend_IAS.demo.infrastructure.r2dbc.entity.CustomerEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface CustomerR2dbcRepository extends R2dbcRepository<CustomerEntity, String> {

    @Query("""
            SELECT customer_id, status, approval_limit
            FROM customers
            WHERE customer_id = :customerId
            FOR UPDATE
            """)
    Mono<CustomerEntity> findWithLock(@Param("customerId") String customerId);
}
