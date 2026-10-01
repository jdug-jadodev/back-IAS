package com.backend_IAS.demo.domain.rule;

import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.entity.Customer;
import com.backend_IAS.demo.domain.enums.CustomerStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import com.backend_IAS.demo.domain.factory.CreditDecisionFactory;
import com.backend_IAS.demo.exception.message.DomainMessages;
import java.math.BigDecimal;
import java.util.Objects;

public final class ApprovalRules {

    public static final int MIN_TERM_MONTHS = 6;
    public static final int MAX_TERM_MONTHS = 60;

    public CreditDecision evaluate(ApplicationData data, Customer customer, BigDecimal totalApproved) {
        Objects.requireNonNull(data, DomainMessages.DATA_REQUIRED);
        Objects.requireNonNull(customer, DomainMessages.CUSTOMER_REQUIRED);
        Objects.requireNonNull(totalApproved, DomainMessages.TOTAL_APPROVED_REQUIRED);
        BigDecimal amount = Objects.requireNonNull(data.getAmount(), DomainMessages.AMOUNT_REQUIRED);
        Integer termMonths = Objects.requireNonNull(data.getTermMonths(), DomainMessages.TERM_MONTHS_REQUIRED);
        CustomerStatus status = Objects.requireNonNull(customer.getStatus(), DomainMessages.CUSTOMER_STATUS_REQUIRED);
        BigDecimal creditLimit = Objects.requireNonNull(customer.getCreditLimit(), DomainMessages.CREDIT_LIMIT_REQUIRED);

        if (!Objects.equals(data.getCustomerId(), customer.getCustomerId())) {
            throw new IllegalArgumentException(DomainMessages.CUSTOMER_APPLICATION_MISMATCH);
        }
        if (totalApproved.signum() < 0 || creditLimit.signum() < 0) {
            throw new IllegalArgumentException(DomainMessages.NEGATIVE_APPROVED_TOTAL_OR_CREDIT_LIMIT);
        }
        if (amount.signum() <= 0) {
            return CreditDecisionFactory.rejected(RejectionReason.INVALID_AMOUNT);
        }
        if (termMonths < MIN_TERM_MONTHS || termMonths > MAX_TERM_MONTHS) {
            return CreditDecisionFactory.rejected(RejectionReason.INVALID_TERM);
        }
        if (status != CustomerStatus.ELIGIBLE) {
            return CreditDecisionFactory.rejected(RejectionReason.CUSTOMER_BLOCKED);
        }
        if (totalApproved.add(amount).compareTo(creditLimit) > 0) {
            return CreditDecisionFactory.rejected(RejectionReason.INSUFFICIENT_LIMIT);
        }
        return CreditDecisionFactory.approved();
    }
}
