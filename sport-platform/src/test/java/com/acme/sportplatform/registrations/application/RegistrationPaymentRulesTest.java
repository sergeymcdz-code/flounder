package com.acme.sportplatform.registrations.application;

import static org.assertj.core.api.Assertions.*;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.EventPaymentSettings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

class RegistrationPaymentRulesTest {
    @Test
    void feesRespectModeDisabledAndFirstConfirmedRegistration() {
        var discipline =
                new EventPaymentSettings(
                        true, "", EventPaymentSettings.FeeMode.PER_DISCIPLINE, null, "RUB", 3);
        var event =
                new EventPaymentSettings(
                        true,
                        "",
                        EventPaymentSettings.FeeMode.PER_EVENT,
                        new BigDecimal("900"),
                        "RUB",
                        3);
        assertThat(RegistrationFeeService.calculate(discipline, new BigDecimal("500"), false))
                .isEqualByComparingTo("500");
        assertThat(RegistrationFeeService.calculate(event, new BigDecimal("500"), false))
                .isEqualByComparingTo("900");
        assertThat(RegistrationFeeService.calculate(event, new BigDecimal("500"), true)).isZero();
        assertThat(
                        RegistrationFeeService.calculate(
                                new EventPaymentSettings(false, "", null, null, "RUB", 3),
                                new BigDecimal("500"),
                                false))
                .isZero();
    }

    @ParameterizedTest
    @CsvSource({
        "CONFIRMED,PENDING,MARK_PAID",
        "ADMITTED,PENDING,MARK_PAID",
        "CONFIRMED,PENDING,WAIVE",
        "ADMITTED,PAID,REFUND",
        "PAID,PAID,RESET",
        "PAID,WAIVED,RESET",
        "PAID,REFUNDED,RESET"
    })
    void allowed(String status, String payment, String action) {
        assertThatCode(() -> RegistrationPaymentService.assertTransition(status, payment, action))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
        "SUBMITTED,PENDING,MARK_PAID",
        "PAID,PAID,MARK_PAID",
        "CONFIRMED,WAIVED,MARK_PAID",
        "SUBMITTED,PENDING,WAIVE",
        "CONFIRMED,PENDING,REFUND",
        "CONFIRMED,PENDING,RESET",
        "WITHDRAWN,PENDING,MARK_PAID"
    })
    void prohibited(String status, String payment, String action) {
        assertThatThrownBy(
                        () -> RegistrationPaymentService.assertTransition(status, payment, action))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectedAndWithdrawnPaidEntriesRemainInapplicable() {
        for (String status : java.util.List.of("REJECTED", "WITHDRAWN")) {
            assertThatThrownBy(
                            () ->
                                    RegistrationPaymentService.assertTransition(
                                            status, "PAID", "MARK_PAID"))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e ->
                                    assertThat(e.getCode())
                                            .isEqualTo("registrations.payment_not_applicable"));
        }
    }
}
