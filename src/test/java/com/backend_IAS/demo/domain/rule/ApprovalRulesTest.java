package com.backend_IAS.demo.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.backend_IAS.demo.domain.entity.Customer;
import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.enums.CustomerStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ApprovalRulesTest {

    private final ApprovalRules rules = new ApprovalRules();

    @Test
    void shouldApproveWhenAllRulesAreMet() {
        CreditDecision decision = rules.evaluate(
                application("4000000", 12),
                customer(CustomerStatus.ELIGIBLE, "10000000"),
                new BigDecimal("3000000")
        );

        assertTrue(decision.isApproved());
    }

    @Test
    void shouldApproveWhenCreditLimitIsReachedExactly() {
        CreditDecision decision = rules.evaluate(
                application("6000000.00", 60),
                customer(CustomerStatus.ELIGIBLE, "10000000"),
                new BigDecimal("4000000")
        );

        assertTrue(decision.isApproved());
    }

    @Test
    void shouldAcceptTermBoundaries() {
        CreditDecision minimumTerm = rules.evaluate(
                application("1000000", 6),
                customer(CustomerStatus.ELIGIBLE, "10000000"),
                BigDecimal.ZERO
        );
        CreditDecision maximumTerm = rules.evaluate(
                application("1000000", 60),
                customer(CustomerStatus.ELIGIBLE, "10000000"),
                BigDecimal.ZERO
        );

        assertTrue(minimumTerm.isApproved());
        assertTrue(maximumTerm.isApproved());
    }

    @Test
    void shouldRejectZeroOrNegativeAmount() {
        assertRejected(
                rules.evaluate(
                        application("0", 12),
                        customer(CustomerStatus.ELIGIBLE, "10000000"),
                        BigDecimal.ZERO
                ),
                RejectionReason.INVALID_AMOUNT
        );
        assertRejected(
                rules.evaluate(
                        application("-1", 12),
                        customer(CustomerStatus.ELIGIBLE, "10000000"),
                        BigDecimal.ZERO
                ),
                RejectionReason.INVALID_AMOUNT
        );
    }

    @Test
    void shouldRejectTermOutsideRange() {
        assertRejected(
                rules.evaluate(
                        application("1000000", 5),
                        customer(CustomerStatus.ELIGIBLE, "10000000"),
                        BigDecimal.ZERO
                ),
                RejectionReason.INVALID_TERM
        );
        assertRejected(
                rules.evaluate(
                        application("1000000", 61),
                        customer(CustomerStatus.ELIGIBLE, "10000000"),
                        BigDecimal.ZERO
                ),
                RejectionReason.INVALID_TERM
        );
    }

    @Test
    void shouldRejectBlockedCustomer() {
        CreditDecision decision = rules.evaluate(
                application("1000000", 12),
                customer(CustomerStatus.BLOCKED, "10000000"),
                BigDecimal.ZERO
        );

        assertRejected(decision, RejectionReason.CUSTOMER_BLOCKED);
    }

    @Test
    void shouldRejectWhenCreditLimitIsExceeded() {
        CreditDecision decision = rules.evaluate(
                application("6000000", 12),
                customer(CustomerStatus.ELIGIBLE, "10000000"),
                new BigDecimal("4000000.01")
        );

        assertRejected(decision, RejectionReason.INSUFFICIENT_LIMIT);
    }

    @Test
    void shouldApplyRejectionReasonsInDeterministicOrder() {
        CreditDecision decision = rules.evaluate(
                application("0", 1),
                customer(CustomerStatus.BLOCKED, "0"),
                BigDecimal.ZERO
        );

        assertRejected(decision, RejectionReason.INVALID_AMOUNT);
    }

    private void assertRejected(CreditDecision decision, RejectionReason expectedReason) {
        assertTrue(decision.isRejected());
        assertEquals(expectedReason, decision.getReason());
    }

    private ApplicationData application(String amount, int termMonths) {
        return new ApplicationData("REF-001", "CLI-1001", new BigDecimal(amount), termMonths);
    }

    private Customer customer(CustomerStatus status, String creditLimit) {
        return new Customer("CLI-1001", status, new BigDecimal(creditLimit));
    }
}
