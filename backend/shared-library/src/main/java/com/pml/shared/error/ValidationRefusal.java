package com.pml.shared.error;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Input that failed validation.
 *
 * <h2>One code, one error, however many fields are wrong</h2>
 * There are no per-constraint codes, deliberately: a form needs the list of bad
 * fields, not a hundred codes describing kinds of badness. So every validation
 * failure is {@code COMMAND_NOT_WELL_FORMED} and the detail lives in
 * {@code extensions.fields}.
 *
 * <p>One <em>error</em> as well as one code. Returning an error per violation
 * gives the client an array whose length depends on how wrong the input was, so
 * the code that renders "the first error" shows a different field each time and
 * the code that renders all of them shows the same banner repeatedly. A single
 * error with a field list is what lets each message land next to its own
 * input.</p>
 *
 * <h2>The fields are sorted</h2>
 * Bean Validation returns violations in an unspecified order — it is a
 * {@code Set}. Unsorted, the same bad request produces a differently-ordered
 * list on each call, which makes responses non-reproducible in tests and
 * reorders the errors under a user's fields between two attempts.
 */
public final class ValidationRefusal extends DomainRefusal {

    private final List<FieldViolation> violations;

    public ValidationRefusal(List<FieldViolation> violations) {
        super(ErrorCode.COMMAND_NOT_WELL_FORMED,
                developerMessage(violations),
                fieldsExtension(violations));
        this.violations = sorted(violations);
    }

    public List<FieldViolation> violations() {
        return violations;
    }

    private static List<FieldViolation> sorted(List<FieldViolation> violations) {
        return violations == null ? List.of() : violations.stream()
                .sorted(Comparator.comparing(FieldViolation::path)
                        .thenComparing(FieldViolation::constraint))
                .toList();
    }

    private static Map<String, Object> fieldsExtension(List<FieldViolation> violations) {
        Map<String, Object> extension = new LinkedHashMap<>();
        extension.put("fields", sorted(violations).stream()
                .map(FieldViolation::asExtension)
                .toList());
        return extension;
    }

    /**
     * For the log. Paths and constraint names only — the same restraint the wire
     * gets, because a developer message that named the rejected values would be
     * the one place the input is written down in full.
     */
    private static String developerMessage(List<FieldViolation> violations) {
        return "input failed validation: " + sorted(violations).stream()
                .map(violation -> violation.path() + " (" + violation.constraint() + ")")
                .toList();
    }
}
