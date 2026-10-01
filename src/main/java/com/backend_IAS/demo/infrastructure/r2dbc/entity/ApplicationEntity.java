package com.backend_IAS.demo.infrastructure.r2dbc.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
@Table("credit_applications")
public class ApplicationEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("application_reference")
    private String applicationReference;

    @Column("requested_customer_id")
    private String requestedCustomerId;

    @Column("customer_id")
    private String identifiedCustomerId;

    @Column("amount")
    private BigDecimal amount;

    @Column("term_months")
    private Integer termMonths;

    @Column("status")
    private String status;

    @Column("reason_code")
    private String reasonCode;

    @Column("reason")
    private String reason;

    @Column("processed_at")
    private OffsetDateTime processedAt;
}
