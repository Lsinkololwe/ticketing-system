package com.pml.identity.web.graphql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The buyer password and phone-OTP paths are deleted, not hidden: no class, no schema entry and no
 * placeholder-email creation is left to be called by accident or revived by a merge.
 */
@Tag("L4")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · the old buyer authentication paths are gone from code and schema")
class LegacyBuyerAuthRemovedTest {

    private static final Path SDL = Path.of("src/main/resources/graphql/schema.graphqls");
    private static final Path MAIN = Path.of("src/main/java");

    @Test
    @DisplayName("the classes that issued, refreshed or checked tokens for a buyer no longer exist")
    void classesAreGone() {
        for (String name : new String[]{
                "com.pml.identity.web.graphql.mutation.PhoneOtpMutationResolver",
                "com.pml.identity.infrastructure.keycloak.KeycloakAuthService",
                "com.pml.identity.web.graphql.dto.auth.PhoneAuthPayload",
                "com.pml.identity.web.graphql.dto.auth.OtpRequestResponse",
                "com.pml.identity.web.graphql.dto.auth.AuthPayload",
                "com.pml.identity.web.graphql.dto.auth.RegisterInput",
                "com.pml.identity.web.graphql.dto.auth.TokenValidation",
                "com.pml.identity.web.rest.dto.KeycloakUserDataDto"}) {
            assertThatThrownBy(() -> Class.forName(name)).as(name).isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    @DisplayName("the schema offers no login, register, refresh, validate, OTP, verification or password operation")
    void schemaIsClean() throws IOException {
        String sdl = Files.readString(SDL);
        for (String gone : new String[]{"login(", "register(", "refreshToken(", "validateToken(", "requestPhoneOtp",
                "verifyPhoneOtp", "AuthPayload", "PhoneAuthPayload", "OtpRequestResponse", "TokenValidation",
                "RegisterInput", "sendPhoneVerification", "verifyPhone(", "sendEmailVerification", "verifyEmail(",
                "syncEmailVerificationStatus", "changePassword", "resetPassword"}) {
            assertThat(sdl).as(gone).doesNotContain(gone);
        }
        assertThat(sdl).contains("logout: Boolean!");
    }

    @Test
    @DisplayName("the Contact type exposes the masked value and never the hash or the ciphertext")
    void contactTypeIsMasked() throws IOException {
        String sdl = Files.readString(SDL);
        int start = sdl.indexOf("type Contact {");
        assertThat(start).isPositive();
        String contact = sdl.substring(start, sdl.indexOf("}", start));
        assertThat(contact).contains("valueMasked").doesNotContain("valueHash").doesNotContain("valueEncrypted")
                .doesNotContain("value:");
    }

    @Test
    @DisplayName("no placeholder email is made up for a phone-only user, and no duplicate phone normaliser remains in the buyer path")
    void noPlaceholders() throws IOException {
        try (var walk = Files.walk(MAIN)) {
            for (Path file : walk.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.toString().contains("/migration/")) {
                    continue; // the migrations recognise the old placeholders in order to clean them up
                }
                String source = Files.readString(file);
                assertThat(source).as(file.getFileName().toString()).doesNotContain("@phone.local");
                if (file.toString().contains("/web/graphql/") || file.toString().contains("/service/impl/User")) {
                    assertThat(source).as(file.getFileName().toString()).doesNotContain("normalizePhoneNumber(");
                }
            }
        }
    }
}
