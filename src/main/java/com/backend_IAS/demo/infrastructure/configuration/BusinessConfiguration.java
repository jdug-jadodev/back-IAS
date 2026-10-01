package com.backend_IAS.demo.infrastructure.configuration;

import com.backend_IAS.demo.domain.rule.ApprovalRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessConfiguration {

    @Bean
    public ApprovalRules approvalRules() {
        return new ApprovalRules();
    }
}
