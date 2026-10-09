package com.pml.shared.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bean Validation's several failure shapes, reduced to one refusal.
 *
 * <h2>Why more than one shape has to be handled</h2>
 * The same annotation reports differently depending on where it is checked.
 * Method validation on a GraphQL resolver argument throws
 * {@code ConstraintViolationException}; body binding on a REST controller
 * throws a {@code BindingResult}-carrying exception. Handling only the first
 * would leave the REST surface returning framework-shaped errors while the
 * GraphQL surface returns the contract — the same divergence one error path for
 * both transports exists to prevent, arrived at by omission rather than by choice.
 */
public final class ValidationTranslation {

    private ValidationTranslation() {
    }

    /** The refusal for this exception, if it is a validation failure at all. */
    public static Optional<DomainRefusal> of(Throwable exception) {
        return switch (exception) {
            case ConstraintViolationException violations ->
                    Optional.of(new ValidationRefusal(fromConstraints(violations)));
            case org.springframework.validation.BindException binding ->
                    Optional.of(new ValidationRefusal(fromBinding(binding.getBindingResult())));
            default -> Optional.empty();
        };
    }

    private static List<FieldViolation> fromConstraints(ConstraintViolationException exception) {
        List<FieldViolation> violations = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            violations.add(new FieldViolation(
                    inputPath(violation.getPropertyPath().toString()),
                    constraintName(violation)));
        }
        return violations;
    }

    private static List<FieldViolation> fromBinding(BindingResult result) {
        List<FieldViolation> violations = new ArrayList<>();
        for (FieldError error : result.getFieldErrors()) {
            violations.add(new FieldViolation(error.getField(), codeOf(error)));
        }
        for (ObjectError error : result.getGlobalErrors()) {
            // A class-level constraint has no field. Naming the object rather
            // than dropping it matters: a cross-field rule ("end must follow
            // start") is exactly the failure a user cannot diagnose from the
            // individual inputs, and silently omitting it leaves a form that
            // refuses with nothing marked.
            violations.add(new FieldViolation(error.getObjectName(), codeOf(error)));
        }
        return violations;
    }

    /**
     * The constraint's simple name — {@code NotBlank}, {@code Size}.
     *
     * <p>Read from the annotation type rather than from the message, so it stays
     * a stable token and cannot pick up an interpolated value.</p>
     */
    private static String constraintName(ConstraintViolation<?> violation) {
        try {
            return violation.getConstraintDescriptor()
                    .getAnnotation()
                    .annotationType()
                    .getSimpleName();
        } catch (RuntimeException unavailable) {
            return "Invalid";
        }
    }

    /**
     * Spring's error code is already the constraint's simple name for standard
     * annotations, and the first entry is the most specific.
     */
    private static String codeOf(ObjectError error) {
        String[] codes = error.getCodes();
        return codes != null && codes.length > 0 && codes[0] != null
                ? lastSegment(codes[0])
                : "Invalid";
    }

    private static String lastSegment(String code) {
        int lastDot = code.lastIndexOf('.');
        return lastDot >= 0 && lastDot < code.length() - 1 ? code.substring(lastDot + 1) : code;
    }

    /**
     * Strips the method-name segment method validation prepends.
     *
     * <p>A property path from method validation reads
     * {@code createEvent.input.title}; the client sent {@code input.title} and
     * has no way to match a path naming a Java method it never saw. Nothing is
     * stripped when the path has a single segment, which is the field-level
     * case.</p>
     */
    private static String inputPath(String propertyPath) {
        int firstDot = propertyPath.indexOf('.');
        return firstDot > 0 && firstDot < propertyPath.length() - 1
                ? propertyPath.substring(firstDot + 1)
                : propertyPath;
    }
}
