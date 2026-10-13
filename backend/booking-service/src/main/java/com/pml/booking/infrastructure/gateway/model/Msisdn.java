package com.pml.booking.infrastructure.gateway.model;

import com.pml.shared.util.PhoneNumbers;

import java.util.Optional;

/**
 * The phone number as the mobile-money provider wants it: digits only, with the country code and
 * no plus sign (for a Zambian customer, {@code 260971234567}).
 *
 * <p>A buyer may have typed the number as {@code 0971234567}, {@code +260 97 123 4567} or
 * {@code 971234567}. All of them are the same wallet, so they are turned into one form before a
 * payment, payout or refund is sent, and before the number is used to pick the network (MTN,
 * Airtel, Zamtel). Reading the number is the platform's single phone parser; this class only
 * strips the plus sign the provider does not want.</p>
 *
 * <p>When the text is not a number the parser accepts, the digits are passed through unchanged, as
 * they always were: the provider then refuses the request with its own reason, which the buyer
 * sees, instead of the platform refusing a payment on a rule the product owner has not set.</p>
 */
public final class Msisdn {

    private Msisdn() {
    }

    /** The provider-ready digits for {@code raw}, never null; an empty string when there are no digits. */
    public static String of(String raw) {
        if (raw == null) {
            return "";
        }
        Optional<PhoneNumbers.Parsed> parsed = PhoneNumbers.parseMobile(raw, PhoneNumbers.DEFAULT_REGION);
        if (parsed.isPresent()) {
            String e164 = parsed.get().e164();
            return e164.startsWith("+") ? e164.substring(1) : e164;
        }
        return raw.replaceAll("[^0-9]", "");
    }
}
