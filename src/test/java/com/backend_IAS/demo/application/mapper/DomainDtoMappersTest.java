package com.backend_IAS.demo.application.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.backend_IAS.demo.application.dto.ApplicationDataDto;
import com.backend_IAS.demo.application.dto.CreditApplicationDto;
import com.backend_IAS.demo.application.dto.CreditDecisionDto;
import com.backend_IAS.demo.application.dto.CustomerDto;
import com.backend_IAS.demo.application.dto.ProcessingResultDto;
import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.entity.Customer;
import com.backend_IAS.demo.domain.entity.ProcessingResult;
import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.enums.CustomerStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class DomainDtoMappersTest {

    private static final Instant PROCESSED_AT = Instant.parse("2026-10-01T12:00:00.123456Z");
    private static final String HISTORICAL_REASON = "El cliente no estaba registrado al procesar la solicitud";

    @ParameterizedTest
    @CsvSource({"1000000.123456789123456789, 12", "-0.000000000000000001, 5", "0.00, 61"})
    void shouldPreserveAmountsAndBusinessRejectionInputs(String amount, int termMonths) {
        ApplicationData original = ApplicationData.builder()
                .applicationReference(" REF-001 ")
                .idempotencyKey("83b36c7f-6a2f-466a-8581-d9ac7f655038")
                .customerId(" CLI-1001 ")
                .amount(new BigDecimal(amount))
                .termMonths(termMonths)
                .build();

        ApplicationDataDto dto = ApplicationDataDtoMapper.toDto(original);

        assertEquals(" REF-001 ", dto.getApplicationReference());
        assertEquals(" CLI-1001 ", dto.getCustomerId());
        assertEquals(new BigDecimal(amount), dto.getAmount());
        assertEquals(termMonths, dto.getTermMonths());
        assertEquals(original, ApplicationDataDtoMapper.toDomain(dto));
    }

    @Test
    void shouldKeepMissingDataAvailableForApplicationValidation() {
        assertNull(ApplicationDataDtoMapper.toDto(null));
        ApplicationData original = ApplicationData.builder().build();

        assertEquals(original, ApplicationDataDtoMapper.toDomain(ApplicationDataDtoMapper.toDto(original)));
    }

    @ParameterizedTest
    @EnumSource(CustomerStatus.class)
    void shouldPreserveCustomerStatusAndExactCreditLimit(CustomerStatus status) {
        Customer original = Customer.builder()
                .customerId("CLI-2001")
                .status(status)
                .creditLimit(new BigDecimal("15000000.000000000000000001"))
                .build();

        CustomerDto dto = CustomerDtoMapper.toDto(original);

        assertEquals("CLI-2001", dto.getCustomerId());
        assertEquals(status, dto.getStatus());
        assertEquals(new BigDecimal("15000000.000000000000000001"), dto.getCreditLimit());
        assertEquals(original, CustomerDtoMapper.toDomain(dto));
    }

    @Test
    void shouldPreserveHistoricalRejectionInsteadOfReplacingItsDescription() {
        CreditDecision original = rejectedDecision();

        CreditDecisionDto dto = CreditDecisionDtoMapper.toDto(original);

        assertEquals(ApplicationStatus.REJECTED, dto.getStatus());
        assertEquals(RejectionReason.CUSTOMER_NOT_FOUND, dto.getReason());
        assertEquals(HISTORICAL_REASON, dto.getReasonDescription());
        assertEquals(original, CreditDecisionDtoMapper.toDomain(dto));
    }

    @Test
    void shouldPreserveApprovalWithoutInventingARejectionReason() {
        CreditDecision original = CreditDecision.builder().status(ApplicationStatus.APPROVED).build();

        CreditDecisionDto dto = CreditDecisionDtoMapper.toDto(original);

        assertEquals(ApplicationStatus.APPROVED, dto.getStatus());
        assertNull(dto.getReason());
        assertNull(dto.getReasonDescription());
        assertEquals(original, CreditDecisionDtoMapper.toDomain(dto));
    }

    @Test
    void shouldPreserveUnknownCustomerAndOriginalProcessingTimestamp() {
        CreditApplication original = application(ApplicationStatus.REJECTED);

        CreditApplicationDto dto = CreditApplicationDtoMapper.toDto(original);

        assertEquals("CLI-MISSING", dto.getData().getCustomerId());
        assertNull(dto.getIdentifiedCustomerId());
        assertEquals(PROCESSED_AT, dto.getProcessedAt());
        assertEquals(HISTORICAL_REASON, dto.getDecision().getReasonDescription());
        assertEquals(original, CreditApplicationDtoMapper.toDomain(dto));
    }

    @Test
    void shouldKeepKnownCustomerLinkSeparateFromRequestedCustomerData() {
        CreditApplication original = application(ApplicationStatus.APPROVED).toBuilder()
                .identifiedCustomerId("CLI-IDENTIFIED")
                .processedAt(null)
                .build();

        CreditApplicationDto dto = CreditApplicationDtoMapper.toDto(original);

        assertEquals("CLI-1001", dto.getData().getCustomerId());
        assertEquals("CLI-IDENTIFIED", dto.getIdentifiedCustomerId());
        assertNull(dto.getProcessedAt());
        assertEquals(original, CreditApplicationDtoMapper.toDomain(dto));
    }

    @ParameterizedTest
    @CsvSource({"APPROVED, true", "APPROVED, false", "REJECTED, true", "REJECTED, false"})
    void shouldPreserveCreatedFlagForBothDecisionStatuses(ApplicationStatus status, boolean created) {
        ProcessingResult original = ProcessingResult.builder()
                .application(application(status))
                .created(created)
                .build();

        ProcessingResultDto dto = ProcessingResultDtoMapper.toDto(original);

        assertEquals(created, dto.isCreated());
        assertEquals(status, dto.getApplication().getDecision().getStatus());
        assertEquals(PROCESSED_AT, dto.getApplication().getProcessedAt());
        assertEquals(original, ProcessingResultDtoMapper.toDomain(dto));
    }

    private CreditDecision rejectedDecision() {
        return CreditDecision.builder()
                .status(ApplicationStatus.REJECTED)
                .reason(RejectionReason.CUSTOMER_NOT_FOUND)
                .reasonDescription(HISTORICAL_REASON)
                .build();
    }

    private CreditApplication application(ApplicationStatus status) {
        boolean approved = status == ApplicationStatus.APPROVED;
        return CreditApplication.builder()
                .data(ApplicationData.builder()
                        .applicationReference("REF-001")
                        .customerId(approved ? "CLI-1001" : "CLI-MISSING")
                        .amount(new BigDecimal("1000.000000000000000001"))
                        .termMonths(12)
                        .build())
                .decision(approved ? CreditDecision.builder().status(status).build() : rejectedDecision())
                .identifiedCustomerId(approved ? "CLI-1001" : null)
                .processedAt(PROCESSED_AT)
                .build();
    }
}
