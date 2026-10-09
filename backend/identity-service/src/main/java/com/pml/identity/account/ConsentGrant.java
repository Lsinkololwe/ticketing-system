package com.pml.identity.account;

/** A consent the person gave while signing in, for example {@code TERMS} at {@code 2026-10}. */
public record ConsentGrant(String purpose, String version) {
}
