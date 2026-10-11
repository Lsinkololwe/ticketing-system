package com.pml.booking.security;

/** How an account number is shown: never in full after it has been entered. */
public final class AccountNumberMask {

    private static final String STARS = "****";

    private AccountNumberMask() {
    }

    public static String of(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return STARS;
        }
        return STARS + accountNumber.substring(accountNumber.length() - 4);
    }
}
