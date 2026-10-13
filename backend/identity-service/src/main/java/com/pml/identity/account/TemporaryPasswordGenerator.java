package com.pml.identity.account;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Makes the one-time password a new staff member is given before their first sign-in.
 *
 * <p>The value is for a person to read and type once: it is {@value #LENGTH} characters, drawn from
 * letters, digits and a few symbols, with the look-alike characters left out (no {@code 0 O o}, no
 * {@code 1 l I}), so reading it aloud or off a screen does not go wrong. It always holds at least three
 * capitals, three lower-case letters, three digits and two symbols, which is well past the staff realm's
 * rules (twelve characters, not the username, not the email address, not one of the last three
 * passwords). Every character and its position come from {@link SecureRandom}; the value is never
 * derived from the person's details, so it cannot be guessed from them.</p>
 *
 * <p>Nothing here stores or logs the value: it is returned to the one caller that asked for it.</p>
 */
@Component
public class TemporaryPasswordGenerator {

    /** How many characters a generated password has. */
    public static final int LENGTH = 20;

    static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    static final String DIGITS = "23456789";
    static final String SYMBOLS = "!#$%*+-=?@";

    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;

    private final SecureRandom random;

    public TemporaryPasswordGenerator() {
        this(new SecureRandom());
    }

    TemporaryPasswordGenerator(SecureRandom random) {
        this.random = random;
    }

    /** A new password; every call returns an unrelated one. */
    public String generate() {
        char[] password = new char[LENGTH];
        int next = 0;
        next = fill(password, next, UPPER, 3);
        next = fill(password, next, LOWER, 3);
        next = fill(password, next, DIGITS, 3);
        next = fill(password, next, SYMBOLS, 2);
        fill(password, next, ALL, LENGTH - next);
        // The guaranteed characters were placed first; a shuffle gives them no fixed position.
        for (int i = password.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char swap = password[i];
            password[i] = password[j];
            password[j] = swap;
        }
        return new String(password);
    }

    private int fill(char[] into, int from, String alphabet, int count) {
        for (int i = 0; i < count; i++) {
            into[from + i] = alphabet.charAt(random.nextInt(alphabet.length()));
        }
        return from + count;
    }
}
