package com.backend_IAS.demo.application.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.backend_IAS.demo.application.dto.ApplicationRequestDto;
import com.backend_IAS.demo.application.dto.ApplicationResponseDto;
import com.backend_IAS.demo.domain.entity.ApplicationData;
import com.backend_IAS.demo.domain.entity.CreditApplication;
import com.backend_IAS.demo.domain.entity.CreditDecision;
import com.backend_IAS.demo.domain.entity.ProcessingResult;
import com.backend_IAS.demo.domain.enums.ApplicationStatus;
import com.backend_IAS.demo.domain.enums.RejectionReason;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationDtoMapperTest {

    private static final Instant PROCESSED_AT = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    void shouldNotNormalizeIdentifiersOrRejectBusinessValuesWhileMappingRequest() {
        ApplicationRequestDto request = ApplicationRequestDto.builder()
                .applicationReference(" REF-001 ")
                .customerId(" CLI-1001 ")
                .amount(new BigDecimal("-0.000000000000000001"))
                .termMonths(5)
                .build();

        ApplicationData data = ApplicationDtoMapper.toDomain(request);

        assertEquals(" REF-001 ", data.getApplicationReference());
        assertEquals(" CLI-1001 ", data.getCustomerId());
        assertEquals(new BigDecimal("-0.000000000000000001"), data.getAmount());
        assertEquals(5, data.getTermMonths());
    }

    @Test
    void shouldLeaveMissingRequestFieldsForTheValidator() {
        ApplicationData data = ApplicationDtoMapper.toDomain(ApplicationRequestDto.builder().build());

        assertNull(data.getApplicationReference());
        assertNull(data.getCustomerId());
        assertNull(data.getAmount());
        assertNull(data.getTermMonths());
    }

    @ParameterizedTest
    @CsvSource({
            "APPROVED, true, Esta solicitud fue aprobada",
            "APPROVED, false, Esta solicitud ya fue aprobada",
            "REJECTED, true, Esta solicitud fue rechazada",
            "REJECTED, false, Esta solicitud ya fue rechazada"
    })
    void shouldDistinguishNewDecisionsFromRetriesWithoutChangingOriginalData(
            ApplicationStatus status, boolean created, String expectedMessage) {
        CreditApplication application = application(status, new BigDecimal("1000000.00"), "Motivo histórico");
        ProcessingResult result = ProcessingResult.builder().application(application).created(created).build();

        ApplicationResponseDto response = ApplicationDtoMapper.toResponse(result);

        assertEquals(expectedMessage, response.getMessage());
        assertEquals(status.name(), response.getStatus());
        assertEquals("REF-001", response.getApplicationReference());
        assertEquals("CLI-1001", response.getCustomerId());
        assertEquals("1000000.00", response.getAmount());
        assertEquals(12, response.getTermMonths());
        assertEquals(PROCESSED_AT, response.getProcessedAt());
        if (status == ApplicationStatus.APPROVED) {
            assertNull(response.getReasonCode());
            assertNull(response.getReason());
        } else {
            assertEquals("INSUFFICIENT_LIMIT", response.getReasonCode());
            assertEquals("Motivo histórico", response.getReason());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "1E+7, 10000000",
            "0.000000000000000001, 0.000000000000000001",
            "1000000.123456789123456789, 1000000.123456789123456789"
    })
    void shouldReturnDecimalTextWithoutRoundingOrScientificNotation(String amount, String expected) {
        ApplicationResponseDto response = ApplicationDtoMapper.toResponse(
                application(ApplicationStatus.APPROVED, new BigDecimal(amount), null));

        assertEquals(expected, response.getAmount());
    }

    @Test
    void shouldUseHistoricalRejectionDescriptionForQueries() {
        ApplicationResponseDto response = ApplicationDtoMapper.toResponse(
                application(ApplicationStatus.REJECTED, BigDecimal.ONE, "El cupo estaba agotado al evaluar"));

        assertEquals("El cupo estaba agotado al evaluar", response.getReason());
        assertEquals("Esta solicitud fue rechazada", response.getMessage());
    }

    @Test
    void shouldUseCurrentDescriptionWhenHistoricalRejectionDescriptionIsMissing() {
        ApplicationResponseDto response = ApplicationDtoMapper.toResponse(
                application(ApplicationStatus.REJECTED, BigDecimal.ONE, null));

        assertEquals("El cupo disponible es insuficiente", response.getReason());
        assertEquals("INSUFFICIENT_LIMIT", response.getReasonCode());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldExposeRequestedUnknownCustomerInResponse(boolean created) {
        CreditApplication application = application(ApplicationStatus.REJECTED, BigDecimal.ONE, "El cliente no existe")
                .toBuilder()
                .data(ApplicationData.builder()
                        .applicationReference("REF-UNKNOWN")
                        .customerId("CLI-MISSING")
                        .amount(BigDecimal.ONE)
                        .termMonths(12)
                        .build())
                .decision(CreditDecision.builder()
                        .status(ApplicationStatus.REJECTED)
                        .reason(RejectionReason.CUSTOMER_NOT_FOUND)
                        .reasonDescription("El cliente no existe")
                        .build())
                .identifiedCustomerId(null)
                .build();

        ApplicationResponseDto response = ApplicationDtoMapper.toResponse(
                ProcessingResult.builder().application(application).created(created).build());

        assertEquals("CLI-MISSING", response.getCustomerId());
        assertEquals("CUSTOMER_NOT_FOUND", response.getReasonCode());
        assertEquals(PROCESSED_AT, response.getProcessedAt());
    }

    private CreditApplication application(ApplicationStatus status, BigDecimal amount, String reasonDescription) {
        return CreditApplication.builder()
                .data(ApplicationData.builder()
                        .applicationReference("REF-001")
                        .customerId("CLI-1001")
                        .amount(amount)
                        .termMonths(12)
                        .build())
                .decision(CreditDecision.builder()
                        .status(status)
                        .reason(status == ApplicationStatus.REJECTED ? RejectionReason.INSUFFICIENT_LIMIT : null)
                        .reasonDescription(status == ApplicationStatus.REJECTED ? reasonDescription : null)
                        .build())
                .identifiedCustomerId("CLI-1001")
                .processedAt(PROCESSED_AT)
                .build();
    }
}
