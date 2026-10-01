package com.backend_IAS.demo.domain.rule;

import com.backend_IAS.demo.domain.entity.ApplicationData;
import java.util.Objects;

public final class ApplicationMatchingRules {

    private ApplicationMatchingRules() {
    }

    public static boolean matches(ApplicationData original, ApplicationData other) {
        Objects.requireNonNull(original, "original is required");
        return other != null
                && Objects.equals(original.getApplicationReference(), other.getApplicationReference())
                && Objects.equals(original.getCustomerId(), other.getCustomerId())
                && original.getAmount() != null
                && other.getAmount() != null
                && original.getAmount().compareTo(other.getAmount()) == 0
                && Objects.equals(original.getTermMonths(), other.getTermMonths());
    }
}
