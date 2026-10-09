package com.pml.shared.error;

import java.util.Map;

/**
 * One offending input field, as rendered under {@code extensions.fields}.
 *
 * @param path       the input path the client sent, e.g. {@code input.title}
 * @param constraint the constraint's <em>name</em> — {@code NotBlank},
 *                   {@code Size}, {@code Email}
 *
 * <h2>The constraint name, never the message</h2>
 * The reason is not tidiness. A validation
 * message is a template that interpolates what it rejected: a
 * {@code @Pattern} on a phone number renders the number, a custom validator on
 * a promo code renders the code. Returning messages would hand back the
 * caller's own input processed through server-side rules, and on any field
 * checked against stored data it hands back more than that.
 *
 * <p>A name is also the thing a client can actually branch on. "must not be
 * blank" is English; {@code NotBlank} is a token that survives translation and
 * renders next to the field in whatever language the user reads.</p>
 */
public record FieldViolation(String path, String constraint) {

    /** The shape put on the wire: {@code { path, constraint }}. */
    public Map<String, Object> asExtension() {
        return Map.of("path", path, "constraint", constraint);
    }
}
