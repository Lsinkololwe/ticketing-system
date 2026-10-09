package com.pml.identity.organization;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The acceptance page sees five fields, and none of them is a credential.
 *
 * <h2>Why a bearer credential makes this a security type rather than a DTO</h2>
 * An invitation token arrives by email or WhatsApp and is then forwarded, screenshotted and
 * pasted into group chats. The token is a bearer credential, and anything it exposes is exposed
 * to whoever the link was forwarded to. So the question this type answers is
 * not *what would be convenient on the acceptance page* but *what is safe to tell a stranger*.
 *
 * <p>Answering with the invitation document instead discloses the invitee's {@code email} and
 * {@code phoneNumber} — contact details for a named person — plus the inviter's user id, the
 * personal message, and the token itself. All to whoever holds a link that was meant for one
 * person.
 *
 * <h2>Exactly five, not at most five</h2>
 * Both bounds matter, checked against the composed schema. A sixth is a leak. Fewer means the acceptance page cannot show who is inviting whom, and a page
 * that cannot say that is one people decline out of suspicion.
 */
@Tag("L4")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R4 · InvitationPreview carries exactly five fields, and no credential")
class InvitationPreviewTest {

    private static final Path SDL = Path.of("src/main/resources/graphql/schema.graphqls");

    /** The five preview fields, in schema order. */
    private static final List<String> EXPECTED = List.of(
            "organizationName", "organizationLogoUrl", "proposedRole",
            "inviterDisplayName", "expiresAt");

    /** Fields the invitation document carries that a forwarded link must not disclose. */
    private static final List<String> WITHHELD = List.of(
            "invitationToken", "email", "phoneNumber", "invitedById", "message", "id");

    private static String previewFields;
    private static String sdl;

    @BeforeAll
    static void read() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(SDL), "identity schema not present");
        sdl = Files.readString(SDL);

        Matcher type = Pattern.compile("(?ms)^type\\s+InvitationPreview\\b[^{]*\\{(.*?)^\\}")
                .matcher(sdl);
        assertThat(type.find())
                .as("InvitationPreview is missing — R4's narrow type is the whole requirement")
                .isTrue();
        // Field declarations only; the type's own comment names the fields it withholds.
        previewFields = type.group(1).replaceAll("#[^\\n]*", "");
    }

    @Test
    @DisplayName("ET-ORG-002-R4 · exactly the five fields §4 names, in §4's order")
    void exactlyFiveFields() {
        List<String> declared = Pattern.compile("(?m)^\\s+(\\w+)\\s*:")
                .matcher(previewFields).results()
                .map(match -> match.group(1))
                .toList();

        assertThat(declared)
                .as("a sixth field is a leak; a missing one leaves the acceptance page unable to "
                        + "say who is inviting whom, which is how an invitation gets declined")
                .containsExactlyElementsOf(EXPECTED);
    }

    @Test
    @DisplayName("ET-ORG-002-R4 · the token and the invitee's contact details are not among them")
    void noCredentialAndNoContactDetails() {
        for (String withheld : WITHHELD) {
            assertThat(previewFields)
                    .as("`%s` is reachable by anyone the invitation link was forwarded to", withheld)
                    .doesNotContain(withheld);
        }
    }

    @Test
    @DisplayName("ET-ORG-002-R4 · invitationByToken returns the preview, not the invitation")
    void theQueryReturnsThePreview() {
        // The type being narrow is worth nothing while the query still answers with the wide one.
        Matcher query = Pattern.compile("(?m)^\\s+invitationByToken\\([^)]*\\)\\s*:\\s*(\\w+)")
                .matcher(sdl);
        assertThat(query.find()).as("invitationByToken has moved — re-point this test").isTrue();
        assertThat(query.group(1)).isEqualTo("InvitationPreview");
    }

    @Test
    @DisplayName("ET-ORG-002-R4 · declineInvitation answers a boolean, not the invitation it declined")
    void declineDoesNotEchoTheInvitation() {
        // The same bearer token reaches this mutation, so a wide return type discloses exactly
        // what the preview was narrowed to withhold: the invitee's contact details.
        Matcher mutation = Pattern.compile("(?m)^\\s+declineInvitation\\([^)]*\\)\\s*:\\s*(\\w+!?)")
                .matcher(sdl);
        assertThat(mutation.find()).as("declineInvitation has moved — re-point this test").isTrue();
        assertThat(mutation.group(1)).isEqualTo("Boolean!");
    }

    @Test
    @DisplayName("ET-ORG-002-R4 · TeamInvitation still carries the fields the preview withholds")
    void theWideTypeIsUnchanged() {
        // The fix narrows one query, it does not strip the document. An organizer listing their
        // own pending invitations legitimately needs the invitee's email — that surface is
        // authenticated and scoped, and confusing the two would break the roster page.
        Matcher type = Pattern.compile("(?ms)^type\\s+TeamInvitation\\b[^{]*\\{(.*?)^\\}")
                .matcher(sdl);
        assertThat(type.find()).isTrue();
        assertThat(type.group(1)).contains("email").contains("proposedRole");
    }
}
