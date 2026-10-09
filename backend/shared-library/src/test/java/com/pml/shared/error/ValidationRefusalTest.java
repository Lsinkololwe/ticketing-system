package com.pml.shared.error;

import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation is one code carrying the field list.
 *
 * <h2>The two things the refusal insists on, and why</h2>
 * <ul>
 *   <li><b>One error</b>, not one per violation. An array whose length depends
 *       on how wrong the input was means the client rendering "the first error"
 *       shows a different field each time.</li>
 *   <li><b>The constraint name, never the message.</b> A message template
 *       interpolates what it rejected — a {@code @Pattern} on a phone number
 *       renders the number back. A name is also the only form a client can
 *       translate.</li>
 * </ul>
 */
@Tag("L1")
@Tag("ET-PLT-005")
@DisplayName("ET-PLT-005-R5 · one code, the field list, no messages")
class ValidationRefusalTest {

    private final PlatformDataFetcherExceptionHandler handler =
            new PlatformDataFetcherExceptionHandler(List.of(new PlatformRefusalTranslator()));

    /** An input whose messages deliberately contain values worth not leaking. */
    private record SignUp(
            @NotBlank(message = "username must not be blank") String username,
            @Email(message = "'${validatedValue}' is not a valid address") String email,
            @Size(min = 8, message = "the password 'hunter2' is too short") String password) {
    }

    private static ConstraintViolationException violationsFor(SignUp input) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            return new ConstraintViolationException(validator.validate(input));
        }
    }

    private GraphQLError handle(Throwable thrown) {
        var result = handler
                .handleException(DataFetcherExceptionHandlerParameters
                        .newExceptionParameters().exception(thrown).build())
                .join();
        assertThat(result.getErrors())
                .as("a validation failure is ONE error however many fields are wrong")
                .hasSize(1);
        return result.getErrors().get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fieldsOf(GraphQLError error) {
        return (List<Map<String, Object>>) error.getExtensions().get("fields");
    }

    @Test
    @DisplayName("three bad fields produce one COMMAND_NOT_WELL_FORMED carrying all three")
    void oneErrorListingEveryField() {
        GraphQLError error = handle(violationsFor(new SignUp("", "not-an-address", "short")));

        assertThat(error.getExtensions())
                .containsEntry(GraphQlErrors.ERROR_CODE, ErrorCode.COMMAND_NOT_WELL_FORMED.name())
                .containsEntry(GraphQlErrors.RETRYABLE, false);

        assertThat(fieldsOf(error))
                .as("the client needs every offending field to mark them all at once")
                .extracting(field -> field.get("path"))
                .containsExactlyInAnyOrder("username", "email", "password");
    }

    @Test
    @DisplayName("each field carries the constraint name, not the message")
    void constraintNamesNotMessages() {
        GraphQLError error = handle(violationsFor(new SignUp("", "not-an-address", "short")));

        assertThat(fieldsOf(error))
                .extracting(field -> field.get("constraint"))
                .containsExactlyInAnyOrder("NotBlank", "Email", "Size");

        String rendered = error.getMessage() + error.getExtensions();
        assertThat(rendered)
                .as("""
                    a message template interpolates the rejected value, so returning \
                    messages hands the caller's input back through server-side rules""")
                .doesNotContain("must not be blank")
                .doesNotContain("not a valid address")
                .doesNotContain("hunter2")
                .doesNotContain("is too short");
    }

    @Test
    @DisplayName("the rejected values themselves never appear")
    void rejectedValuesNeverAppear() {
        GraphQLError error = handle(violationsFor(
                new SignUp("", "attacker@evil.example", "short")));

        assertThat(error.getMessage() + error.getExtensions())
                .doesNotContain("attacker@evil.example");
    }

    @Test
    @DisplayName("the field list is ordered, so the same bad input answers the same way twice")
    void fieldsAreOrdered() {
        // Bean Validation returns a Set. Unordered, two identical requests
        // produce differently-ordered lists and the errors under a user's form
        // reshuffle between attempts.
        List<Object> first = fieldsOf(handle(violationsFor(new SignUp("", "bad", "x"))))
                .stream().map(field -> field.get("path")).toList();
        List<Object> second = fieldsOf(handle(violationsFor(new SignUp("", "bad", "x"))))
                .stream().map(field -> field.get("path")).toList();

        assertThat(first).isEqualTo(second).isSorted();
    }

    @Test
    @DisplayName("the method-name segment is stripped from the path")
    void pathIsTheInputPathNotTheJavaPath() {
        // Method validation reports `createEvent.input.title`. The client sent
        // `input.title` and cannot match a path naming a Java method.
        ValidationRefusal refusal = new ValidationRefusal(
                List.of(new FieldViolation("input.title", "NotBlank")));

        assertThat(refusal.violations())
                .singleElement()
                .extracting(FieldViolation::path)
                .isEqualTo("input.title");
    }

    @Test
    @DisplayName("an element of a list argument keeps its index in the path")
    void listElementsKeepTheirIndex() throws Exception {
        // Bulk mutations take List<@Valid SomethingInput>. Without the index the
        // client is told "a name is blank" across a form of twenty invitations
        // and cannot mark which one, which is the whole value of a field list.
        record Item(@NotBlank String name) {
        }
        class Target {
            public void accept(@jakarta.validation.Valid
                               List<@jakarta.validation.Valid Item> items) {
            }
        }

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().forExecutables().validateParameters(
                    new Target(),
                    Target.class.getMethod("accept", List.class),
                    new Object[]{List.of(new Item("ok"), new Item(""))});

            GraphQLError error = handle(new ConstraintViolationException(violations));

            assertThat(fieldsOf(error))
                    .singleElement()
                    .satisfies(field -> {
                        assertThat(field.get("path")).isEqualTo("items[1].name");
                        assertThat(field.get("constraint")).isEqualTo("NotBlank");
                    });
        }
    }

    @Test
    @DisplayName("a valid input produces no violations at all")
    void validInputIsNotRefused() {
        ConstraintViolationException none =
                violationsFor(new SignUp("someone", "someone@example.com", "longenough"));

        assertThat(none.getConstraintViolations())
                .as("if this were non-empty the tests above would pass for the wrong reason")
                .isEmpty();
    }
}
