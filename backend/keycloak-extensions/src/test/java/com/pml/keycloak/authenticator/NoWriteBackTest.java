package com.pml.keycloak.authenticator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The plugin never creates users, never grants roles, never logs raw contacts or codes. */
@Tag("ET-IDN-001")
@Tag("layer-1-decision")
class NoWriteBackTest {

    @Test
    @DisplayName("ET-IDN-001-R5 · no production source can create a user or grant a role")
    void noUserCreationOrRoleGrants() throws IOException {
        List<String> forbidden = List.of(".addUser(", ".grantRole(", "addRoleMapping", "setSingleAttribute(",
                "setAttribute(\"accountType", "registerUser", "UserProvider.addUser", "System.out", "printStackTrace");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                for (String bad : forbidden) {
                    assertThat(text).as(file + " must not contain " + bad).doesNotContain(bad);
                }
            }
        }
    }

    @Test
    @DisplayName("ET-IDN-001-R5 · no Spring or Gson or phone-otp leftovers in sources and resources")
    void noLegacy() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String text = Files.readString(file).toLowerCase();
                assertThat(text).as(file.toString()).doesNotContain("phoneotp").doesNotContain("phone-otp")
                        .doesNotContain("com.google.gson").doesNotContain("accounttyperolemapper");
            }
        }
    }
}
