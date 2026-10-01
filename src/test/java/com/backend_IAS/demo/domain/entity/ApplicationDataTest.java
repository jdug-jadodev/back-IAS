package com.backend_IAS.demo.domain.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import com.backend_IAS.demo.domain.rule.ApplicationMatchingRules;
import org.junit.jupiter.api.Test;

class ApplicationDataTest {

    @Test
    void shouldMatchAmountsWithDifferentScales() {
        ApplicationData original = application("REF-001", "CLI-1001", "6000000", 12);
        ApplicationData repeated = application("REF-001", "CLI-1001", "6000000.00", 12);

        assertTrue(ApplicationMatchingRules.matches(original, repeated));
    }

    @Test
    void shouldNotMatchWhenBusinessDataChanges() {
        ApplicationData original = application("REF-001", "CLI-1001", "6000000", 12);

        assertFalse(ApplicationMatchingRules.matches(original, application("REF-001", "CLI-2001", "6000000", 12)));
        assertFalse(ApplicationMatchingRules.matches(original, application("REF-001", "CLI-1001", "6000001", 12)));
        assertFalse(ApplicationMatchingRules.matches(original, application("REF-001", "CLI-1001", "6000000", 24)));
    }

    @Test
    void shouldNotMatchNullApplicationData() {
        ApplicationData data = application("REF-001", "CLI-1001", "6000000", 12);

        assertFalse(ApplicationMatchingRules.matches(data, null));
    }

    private ApplicationData application(
            String reference,
            String customerId,
            String amount,
            Integer termMonths
    ) {
        return new ApplicationData(reference, customerId, new BigDecimal(amount), termMonths);
    }
}
