package com.pml.booking.domain;

/** Names as another person is allowed to see them: first name and the initial of the last. */
public final class DisplayNames {

    private DisplayNames() {
    }

    /** {@code "Mary Kasonde Phiri"} becomes {@code "Mary P."}; one word stays as it is; blank is null. */
    public static String firstAndInitial(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        String last = parts[parts.length - 1];
        return parts[0] + " " + Character.toUpperCase(last.charAt(0)) + ".";
    }
}
