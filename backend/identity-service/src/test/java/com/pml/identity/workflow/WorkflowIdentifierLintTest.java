package com.pml.identity.workflow;

import com.pml.identity.infrastructure.temporal.WorkflowIds;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A workflow id and a search attribute are visible to everyone who can open the Temporal UI, in the
 * visibility store, in logs and in metrics labels. A contact in either is a personal-data leak with
 * no way to erase it (D-46), so neither may contain an address or a number.
 *
 * <p>Two halves: the values the account process builds are checked as values, and every call site in
 * the source is checked for what it feeds to {@code WorkflowIds} and to the search-attribute builder.
 */
@Tag("L1")
@Tag("ET-IDN-004")
@Tag("ET-PLT-015")
@DisplayName("ET-IDN-004 · no workflow id or search attribute contains an address or a phone number")
class WorkflowIdentifierLintTest {

    private static final Path SOURCES = Path.of("src/main/java/com/pml/identity");

    /** What an id may never contain. */
    private static final Pattern PERSONAL_VALUE = Pattern.compile("@|\\+\\d");

    /** Variable and method names that hold a contact or a name. */
    private static final Pattern PERSONAL_NAME = Pattern.compile(
            "(?i)email|phone|msisdn|contactValue|rawContact|normalized|address|firstName|lastName|displayName|getValue\\(");

    private static final Pattern WORKFLOW_ID_CALL = Pattern.compile("WorkflowIds\\.(\\w+)\\(([^;]*?)\\)\\s*[,;)]");
    private static final Pattern SEARCH_ATTRIBUTE_CALL = Pattern.compile(
            "ProcessSearchAttributes\\s*\\.\\s*(?:of|builder)\\(([^;]*);|\\.(?:businessId|tenantId|organizationId|eventId|businessStatus)\\(([^;]*?)\\)");

    @Test
    @DisplayName("the account-ensure id is the contact key and nothing a raw contact could pass for")
    void accountEnsureIdIsOpaque() {
        String contactKey = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
        String id = WorkflowIds.accountEnsure(contactKey);

        assertThat(id).isEqualTo("account-ensure/" + contactKey);
        assertThat(PERSONAL_VALUE.matcher(id).find()).isFalse();
        for (String raw : List.of("buyer@example.com", "+260971234567", "260971234567", "Buyer@Example.com", "")) {
            assertThatThrownBy(() -> WorkflowIds.accountEnsure(raw)).as(raw).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("the pattern flags what it exists to flag")
    void patternIsNotVacuous() {
        assertThat(PERSONAL_VALUE.matcher("account-ensure/a@b.c").find()).isTrue();
        assertThat(PERSONAL_VALUE.matcher("account-ensure/+260971234567").find()).isTrue();
        assertThat(PERSONAL_VALUE.matcher("account-ensure/9f86d081884c").find()).isFalse();
        assertThat(PERSONAL_NAME.matcher("WorkflowIds.reminder(user.getEmail())").find()).isTrue();
        assertThat(PERSONAL_NAME.matcher("WorkflowIds.reminder(reminderId)").find()).isFalse();
    }

    @Test
    @DisplayName("no call site builds a workflow id or a search attribute from a contact or a name")
    void callSitesFeedOnlyOpaqueIds() throws IOException {
        List<String> offenders = new ArrayList<>();
        int calls = 0;
        for (Path file : mainSources()) {
            String body = withoutComments(Files.readString(file));
            for (Pattern pattern : List.of(WORKFLOW_ID_CALL, SEARCH_ATTRIBUTE_CALL)) {
                Matcher call = pattern.matcher(body);
                while (call.find()) {
                    calls++;
                    String arguments = call.group(call.groupCount() == 2 && call.group(1) == null ? 2 : 1);
                    arguments = arguments == null ? call.group() : arguments;
                    Matcher personal = PERSONAL_NAME.matcher(arguments);
                    if (personal.find()) {
                        offenders.add("%s: %s".formatted(SOURCES.getParent().getParent().getParent().relativize(file).getFileName(),
                                call.group().replaceAll("\\s+", " ")));
                    }
                }
            }
        }
        assertThat(calls).as("the scan found the call sites").isGreaterThan(10);
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("workflow ids are only ever built by WorkflowIds: no id literal is assembled at a call site")
    void idsAreNotAssembledByHand() throws IOException {
        Pattern handBuilt = Pattern.compile("setWorkflowId\\(\\s*(\"|[A-Za-z_.]+\\s*\\+)");
        List<String> offenders = new ArrayList<>();
        for (Path file : mainSources()) {
            if (handBuilt.matcher(withoutComments(Files.readString(file))).find()) {
                offenders.add(file.getFileName().toString());
            }
        }
        assertThat(offenders).isEmpty();
    }

    private static List<Path> mainSources() throws IOException {
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static String withoutComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
