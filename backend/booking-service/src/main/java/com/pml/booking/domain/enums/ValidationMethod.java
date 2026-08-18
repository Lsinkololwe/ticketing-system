package com.pml.booking.domain.enums;

/**
 * How a ticket was presented at the gate.
 *
 * <p>Recorded on every check-in because the three methods carry very different
 * assurance. {@link #QR_ONLINE} is verified against the server at scan time and
 * is the only one where a duplicate can be refused. {@link #QR_OFFLINE} verifies
 * the signature locally and cannot see other devices, so duplicates are only
 * detectable later, on upload. {@link #MANUAL} bypasses the QR entirely and
 * rests on a steward's judgement.
 *
 * <p>Keeping them distinct is what makes the manual-ratio alert possible: a gate
 * where a large share of admissions are MANUAL is either broken or being worked
 * around, and both are worth knowing about during the event rather than after.
 *
 * @see <a href="file:../../../../../../../specs/ticketing/003-validation-and-checkin/spec.md">ET-TKT-003</a>
 */
public enum ValidationMethod {

    /** QR scanned with a live connection; the server refused or accepted it. */
    QR_ONLINE,

    /**
     * QR scanned with no connection; signature checked locally against a cached
     * event key, uploaded afterwards. A duplicate across two offline devices
     * cannot be prevented — only detected on upload.
     */
    QR_OFFLINE,

    /**
     * Admitted by a steward without a working QR, against a reference number.
     * Requires a reason.
     */
    MANUAL
}
