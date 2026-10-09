package com.pml.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A mutation returns the entity or a purpose-built result, never a success flag.
 *
 * <h2>What the rule is actually for</h2>
 * A {@code { success: Boolean, message: String }} payload puts the outcome of an operation
 * somewhere a client has to remember to look. The HTTP call succeeded, the GraphQL {@code errors}
 * array is empty, the data came back — and the operation did not happen. Every caller must check a
 * field, and the one that forgets fails silently. Refusals belong in {@code errors} with an
 * {@code ErrorCode}, where a client cannot fail to see them.
 *
 * <h2>Why this is a ban and not a ratchet</h2>
 * All 215 mutations across the three subgraphs already comply. There is no baseline of legitimate
 * violations to preserve, so freezing a count would only be a slower ban.
 *
 * <h2>The distinction the allowlist encodes</h2>
 * {@code message} is not banned as a word — it is banned as a <em>failure channel</em>. A
 * {@code TeamInvitation} carries the inviter's personal note; a {@code ValidationResult} carries a
 * sentence a steward reads off a phone at a gate. Both are content the caller asked for. What
 * matters is that neither asks the client to infer failure from it: each carries a
 * machine-readable field — an exhaustive enum, a status — that the client actually branches on.
 */
@Tag("L4")
@Tag("ET-PLT-004")
@DisplayName("ET-PLT-004-R4 · no mutation returns a success/message wrapper")
class MutationShapeLintTest {

    private static final List<String> SUBGRAPHS = List.of("catalog", "booking", "identity");

    /**
     * Types carrying a {@code message} field that is content, not a failure channel.
     *
     * <p>Each names the machine-readable field the client branches on instead, because that is the
     * test of whether a {@code message} is legitimate.</p>
     */
    private static final Map<String, String> MESSAGE_IS_CONTENT = new LinkedHashMap<>(Map.of(
            "TeamInvitation", "the inviter's personal note; status is the machine-readable field",
            "ValidationResult", "a sentence a steward reads at the gate; outcome is an exhaustive "
                    + "CheckInOutcome enum and admitted is derived from it",
            "ApprovalNotification", "the notification's own body — it IS the payload",
            "OrganizerActivityItem", "an activity-feed line; type carries the meaning",
            "FileUploadError", "an error detail type, not a mutation return",
            "SystemAlert", "an operator alert's description; severity and status are the enums a client branches on",
            "SystemAnnouncement", "the announcement body shown to its segment; status is the machine-readable field",
            "BulkOperationError", "a per-row failure inside a bulk result, which is the shape "
                    + "that lets a caller see which rows failed"));

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    private static String sdl(String subgraph) throws IOException {
        return Files.readString(backendRoot()
                        .resolve(subgraph + "-service/src/main/resources/graphql/schema.graphqls"))
                .replaceAll("(?m)#.*$", "");
    }

    /** type name → its field block, for every {@code type X { … }} in a subgraph. */
    private static Map<String, String> typeBlocks(String sdl) {
        Map<String, String> blocks = new LinkedHashMap<>();
        String[] lines = sdl.split("\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher decl = Pattern.compile("^\\s*(?:extend\\s+)?type\\s+(\\w+)").matcher(lines[i]);
            if (decl.find() && lines[i].contains("{")) {
                StringBuilder body = new StringBuilder();
                int j = i + 1;
                while (j < lines.length && !lines[j].startsWith("}")) {
                    body.append(lines[j]).append("\n");
                    j++;
                }
                blocks.merge(decl.group(1), body.toString(), String::concat);
                i = j;
            }
        }
        return blocks;
    }

    private static Set<String> mutationReturnTypes(Map<String, String> blocks) {
        Set<String> returns = new LinkedHashSet<>();
        String mutation = blocks.get("Mutation");
        if (mutation == null) {
            return returns;
        }
        Matcher field = Pattern.compile(
                "^\\s{2,4}(\\w+)\\s*(?:\\((?:[^()]|\\([^()]*\\))*\\))?\\s*:\\s*([\\w!\\[\\]]+)",
                Pattern.MULTILINE).matcher(mutation);
        while (field.find()) {
            returns.add(field.group(2).replace("[", "").replace("]", "").replace("!", ""));
        }
        return returns;
    }

    @Test
    @DisplayName("no mutation returns a type carrying success: Boolean")
    void noMutationReturnsASuccessFlag() throws IOException {
        List<String> offenders = new ArrayList<>();
        int mutations = 0;

        for (String subgraph : SUBGRAPHS) {
            Map<String, String> blocks = typeBlocks(sdl(subgraph));
            Set<String> returns = mutationReturnTypes(blocks);
            mutations += returns.size();
            for (String returned : returns) {
                String body = blocks.get(returned);
                if (body != null && Pattern.compile("^\\s*success:\\s*Boolean", Pattern.MULTILINE)
                        .matcher(body).find()) {
                    offenders.add(subgraph + " · a mutation returns " + returned
                            + ", which carries success: Boolean");
                }
            }
        }

        assertThat(mutations)
                .as("no mutation return types parsed — the Mutation block moved or the regex broke")
                .isGreaterThan(30);
        assertThat(offenders)
                .as("a success flag hides the outcome in the data payload, where an empty errors "
                        + "array and a 200 look like the operation worked. Refuse with a registry "
                        + "code instead — ET-PLT-005.")
                .isEmpty();
    }

    @Test
    @DisplayName("a mutation returning a type with message: String is on the allowlist, with a reason")
    void messageFieldsAreContentNotFailureChannels() throws IOException {
        List<String> unexplained = new ArrayList<>();

        for (String subgraph : SUBGRAPHS) {
            Map<String, String> blocks = typeBlocks(sdl(subgraph));
            for (String returned : mutationReturnTypes(blocks)) {
                String body = blocks.get(returned);
                if (body != null && Pattern.compile("^\\s*message:\\s*String", Pattern.MULTILINE)
                        .matcher(body).find()
                        && !MESSAGE_IS_CONTENT.containsKey(returned)) {
                    unexplained.add(subgraph + " · " + returned
                            + " is returned by a mutation and carries message: String");
                }
            }
        }

        assertThat(unexplained)
                .as("if this message conveys failure it belongs in errors with a registry code; if "
                        + "it is content, add it to MESSAGE_IS_CONTENT naming the machine-readable "
                        + "field the client branches on instead")
                .isEmpty();
    }

    @Test
    @DisplayName("the allowlist only names types that still exist and still carry a message")
    void theAllowlistDoesNotRot() throws IOException {
        Map<String, String> everywhere = new LinkedHashMap<>();
        for (String subgraph : SUBGRAPHS) {
            everywhere.putAll(typeBlocks(sdl(subgraph)));
        }

        List<String> stale = new ArrayList<>();
        for (String allowed : MESSAGE_IS_CONTENT.keySet()) {
            String body = everywhere.get(allowed);
            if (body == null) {
                stale.add(allowed + " no longer exists");
            } else if (!Pattern.compile("^\\s*message:\\s*String", Pattern.MULTILINE)
                    .matcher(body).find()) {
                stale.add(allowed + " no longer carries message: String");
            }
        }

        // An entry for a type that has since lost its message field silently re-permits the field
        // coming back — the exemption outlives the thing it was granted for.
        assertThat(stale)
                .as("delete the stale entry so the rule applies to this type again")
                .isEmpty();
    }
}
