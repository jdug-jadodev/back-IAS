package com.backend_IAS.demo.infrastructure.r2dbc.entity;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Table("customers")
public class CustomerEntity {

    @Id
    @Column("customer_id")
    private String customerId;

    @Column("status")
    private String status;

    @Column("approval_limit")
    private BigDecimal creditLimit;
}
