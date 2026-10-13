package com.pml.booking.infrastructure.gateway.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("L1")
@Tag("ET-PAY-001")
@DisplayName("a buyer's phone number reaches the mobile-money provider in one form")
class MsisdnTest {

    @Test
    @DisplayName("every way of typing the same Zambian number becomes the same digits")
    void sameWalletSameDigits() {
        for (String typed : new String[]{"0971234567", "+260971234567", "260971234567", "+260 97 123 4567",
                "00260971234567", "971234567", "097-123-4567"}) {
            assertThat(Msisdn.of(typed)).as(typed).isEqualTo("260971234567");
        }
    }

    @Test
    @DisplayName("the network is found from any of those forms")
    void networkFromAnyForm() {
        assertThat(MobileNetwork.fromPhoneNumber("0971234567")).isEqualTo(MobileNetwork.MTN);
        assertThat(MobileNetwork.fromPhoneNumber("+260 77 123 4567")).isEqualTo(MobileNetwork.AIRTEL);
        assertThat(MobileNetwork.fromPhoneNumber("260951234567")).isEqualTo(MobileNetwork.ZAMTEL);
    }

    @Test
    @DisplayName("text that is not a number the platform accepts is passed through as digits, for the provider to refuse")
    void unparsableIsPassedThrough() {
        assertThat(Msisdn.of("12345")).isEqualTo("12345");
        assertThat(Msisdn.of("abc")).isEmpty();
        assertThat(Msisdn.of(null)).isEmpty();
    }
}
