package com.pml.shared.graphql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No mutation return type carries a `success` flag.
 *
 * <h2>Why a boolean beside the data is worse than no signal at all</h2>
 * {@code { success: false }} arrives as HTTP 200 with a well-formed body. Every
 * transport-level check passes, the Apollo error link sees nothing, and a client
 * that forgot to read the flag proceeds exactly as though the operation
 * succeeded — showing a confirmation for a payment that never happened.
 *
 * <p>A thrown refusal cannot be missed the same way: it lands in the GraphQL
 * {@code errors} array with a registry code, so the failure is visible to
 * middleware that never heard of this particular mutation.</p>
 *
 * <h2>What this does not forbid</h2>
 * A field named {@code success} on a type that is genuinely reporting an
 * outcome of something else — a per-item result inside a bulk run, a webhook's
 * delivery record — is data, not error signalling. The rule is scoped to types a
 * mutation returns, which is where the confusion lives.
 */
@Tag("L4")
@Tag("ET-PLT-004")
@DisplayName("ET-PLT-004-R4 · mutations return the entity, not a success flag")
class MutationWrapperTest {

    private static final Pattern SUCCESS_FIELD =
            Pattern.compile("^\\s+success\\s*:\\s*Boolean", Pattern.MULTILINE);

    /** `type Name … { … }` including the body. */
    private static final Pattern TYPE_BLOCK =
            Pattern.compile("^type\\s+(\\w+)\\b[^{]*\\{(.*?)^\\}", Pattern.DOTALL | Pattern.MULTILINE);

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    private static List<Path> schemas() {
        List<Path> found = new ArrayList<>();
        for (String service : List.of("booking-service", "catalog-service", "identity-service")) {
            Path schema = backendRoot().resolve(service)
                    .resolve("src/main/resources/graphql/schema.graphqls");
            if (Files.isRegularFile(schema)) {
                found.add(schema);
            }
        }
        return found;
    }

    @Test
    @DisplayName("all three subgraph schemas are read")
    void schemasAreFound() {
        assertThat(schemas())
                .as("with no schema to read, the assertion below passes on nothing")
                .hasSize(3);
    }

    @Test
    @DisplayName("no type declares a success flag")
    void noSuccessFlagAnywhere() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path schema : schemas()) {
            Matcher type = TYPE_BLOCK.matcher(Files.readString(schema));
            while (type.find()) {
                if (SUCCESS_FIELD.matcher(type.group(2)).find()) {
                    offenders.add(backendRoot().relativize(schema) + " → " + type.group(1));
                }
            }
        }

        assertThat(offenders)
                .as("""
                    a success flag returns HTTP 200 for a failure, so a client that does not \
                    read it proceeds as though the operation worked. Return the entity and \
                    raise a DomainRefusal instead — the error then reaches the client through \
                    the errors array, where middleware can see it.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no mutation returns a bare wrapper type")
    void mutationsReturnSomethingUseful() throws IOException {
        // A type whose name ends `MutationResponse` and which survived the
        // success-flag check is still a wrapper by shape. Catching the naming
        // separately means a wrapper cannot be reintroduced simply by leaving
        // the boolean out of it.
        Pattern mutationField = Pattern.compile(
                "^\\s+(\\w+)\\s*(?:\\([^)]*\\))?\\s*:\\s*\\[?(\\w+)", Pattern.MULTILINE);
        List<String> offenders = new ArrayList<>();

        for (Path schema : schemas()) {
            String text = Files.readString(schema);
            Matcher block = Pattern
                    .compile("^type Mutation\\b.*?^\\}", Pattern.DOTALL | Pattern.MULTILINE)
                    .matcher(text);
            if (!block.find()) {
                continue;
            }
            Matcher field = mutationField.matcher(block.group());
            while (field.find()) {
                if (field.group(2).endsWith("MutationResponse")) {
                    offenders.add(backendRoot().relativize(schema)
                            + " → " + field.group(1) + ": " + field.group(2));
                }
            }
        }

        assertThat(offenders)
                .as("return the entity the mutation acted on; a client needs the new state, "
                        + "and a wrapper makes it optional")
                .isEmpty();
    }
}
