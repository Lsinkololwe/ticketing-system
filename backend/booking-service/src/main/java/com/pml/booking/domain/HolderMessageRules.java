package com.pml.booking.domain;

import com.pml.shared.error.FieldViolation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** What an organizer may say to a crowd. Pure, so every limit is a unit test. */
public final class HolderMessageRules {

    public static final int SUBJECT_MIN = 3;
    public static final int SUBJECT_MAX = 80;
    public static final int BODY_MIN = 10;
    public static final int BODY_MAX = 500;
    /** More holders than this and the message is refused rather than sent in part. */
    public static final int MAX_RECIPIENTS = 5000;
    public static final int BATCH = 200;

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");

    private HolderMessageRules() {
    }

    /**
     * How many of a batch identity's answer says it reached: what it counted, never more than was asked, and the whole
     * batch when the answer is that it had already sent this one (a retry).
     */
    public static int delivered(String receiptStatus, int receiptRecipients, int batchSize) {
        return "DUPLICATE".equals(receiptStatus) ? batchSize : Math.max(0, Math.min(receiptRecipients, batchSize));
    }

    /** SENT when everyone was reached, PARTIAL when some were, FAILED when none were. */
    public static com.pml.booking.domain.model.HolderMessage.Status statusOf(int delivered, int recipients) {
        return delivered <= 0 ? com.pml.booking.domain.model.HolderMessage.Status.FAILED
                : delivered < recipients ? com.pml.booking.domain.model.HolderMessage.Status.PARTIAL
                : com.pml.booking.domain.model.HolderMessage.Status.SENT;
    }

    /** Removes control characters (a message is text, not terminal escapes) and trims. */
    public static String clean(String text) {
        return text == null ? null : CONTROL.matcher(text).replaceAll("").trim();
    }

    public static List<FieldViolation> check(String subject, String body) {
        List<FieldViolation> violations = new ArrayList<>();
        String s = clean(subject);
        String b = clean(body);
        if (s == null || s.length() < SUBJECT_MIN || s.length() > SUBJECT_MAX) {
            violations.add(new FieldViolation("input.subject", "must be " + SUBJECT_MIN + " to " + SUBJECT_MAX + " characters"));
        }
        if (b == null || b.length() < BODY_MIN || b.length() > BODY_MAX) {
            violations.add(new FieldViolation("input.message", "must be " + BODY_MIN + " to " + BODY_MAX + " characters"));
        }
        return violations;
    }
}
