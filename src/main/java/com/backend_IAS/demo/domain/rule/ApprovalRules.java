package com.backend_IAS.demo.domain.rule;

import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.entity.Customer;
import com.backend_IAS.demo.domain.enums.CustomerStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import java.math.BigDecimal;
import java.util.Objects;

public final class ApprovalRules {

    public static final int MIN_TERM_MONTHS = 6;
    public static final int MAX_TERM_MONTHS = 60;

    public CreditDecision evaluate(ApplicationData data, Customer customer, BigDecimal totalApproved) {
        Objects.requireNonNull(data, "data is required");
        Objects.requireNonNull(customer, "customer is required");
        Objects.requireNonNull(totalApproved, "totalApproved is required");
        BigDecimal amount = Objects.requireNonNull(data.getAmount(), "amount is required");
        Integer termMonths = Objects.requireNonNull(data.getTermMonths(), "termMonths is required");
        CustomerStatus status = Objects.requireNonNull(customer.getStatus(), "customer status is required");
        BigDecimal creditLimit = Objects.requireNonNull(customer.getCreditLimit(), "creditLimit is required");

        if (!Objects.equals(data.getCustomerId(), customer.getCustomerId())) {
            throw new IllegalArgumentException("Customer does not match application");
        }
        if (totalApproved.signum() < 0 || creditLimit.signum() < 0) {
            throw new IllegalArgumentException("Approved total and credit limit must not be negative");
        }
        if (amount.signum() <= 0) {
            return CreditDecision.rejected(RejectionReason.INVALID_AMOUNT);
        }
        if (termMonths < MIN_TERM_MONTHS || termMonths > MAX_TERM_MONTHS) {
            return CreditDecision.rejected(RejectionReason.INVALID_TERM);
        }
        if (status != CustomerStatus.ELIGIBLE) {
            return CreditDecision.rejected(RejectionReason.CUSTOMER_BLOCKED);
        }
        if (totalApproved.add(amount).compareTo(creditLimit) > 0) {
            return CreditDecision.rejected(RejectionReason.INSUFFICIENT_LIMIT);
        }
        return CreditDecision.approved();
    }
}
