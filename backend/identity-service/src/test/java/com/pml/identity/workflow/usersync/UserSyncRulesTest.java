package com.pml.identity.workflow.usersync;

import com.pml.identity.workflow.usersync.UserSyncWorkflow.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-IDN-002")
@DisplayName("ET-IDN-002-R2 · user-sync rules: originating ids, their memory, and what an event asks for")
class UserSyncRulesTest {

    @Test
    @DisplayName("PLT-015 R6 · an id seen once is new, seen twice is a duplicate")
    void rememberDeduplicates() {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        assertThat(UserSyncRules.remember(seen, "e-1")).isTrue();
        assertThat(UserSyncRules.remember(seen, "e-1")).isFalse();
        assertThat(UserSyncRules.remember(seen, null)).as("no id, nothing to deduplicate on").isTrue();
        assertThat(UserSyncRules.remember(seen, null)).isTrue();
    }

    @Test
    @DisplayName("the memory is bounded and forgets the oldest first")
    void rememberIsBounded() {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (int index = 0; index <= UserSyncRules.SEEN_LIMIT; index++) {
            UserSyncRules.remember(seen, "e-" + index);
        }
        assertThat(seen).hasSize(UserSyncRules.SEEN_LIMIT).doesNotContain("e-0").contains("e-" + UserSyncRules.SEEN_LIMIT);
    }

    @Test
    @DisplayName("a run continues as new when the server suggests it, or when its budget is spent")
    void continueAsNew() {
        assertThat(UserSyncRules.shouldContinueAsNew(true, 1, 1000)).isTrue();
        assertThat(UserSyncRules.shouldContinueAsNew(false, 999, 1000)).isFalse();
        assertThat(UserSyncRules.shouldContinueAsNew(false, 1000, 1000)).isTrue();
        assertThat(UserSyncRules.shouldContinueAsNew(false, UserSyncRules.MAX_CHANGES_PER_RUN, 0)).isTrue();
    }

    @Test
    @DisplayName("an originating id needs Keycloak's timestamp, and is stable for one event")
    void eventId() {
        assertThat(UserSyncRules.eventId("u-1", "LOGIN", null, 0)).isNull();
        assertThat(UserSyncRules.eventId("u-1", "LOGIN", null, 42)).isEqualTo(UserSyncRules.eventId("u-1", "LOGIN", null, 42));
        assertThat(UserSyncRules.eventId("u-1", "LOGIN", null, 42)).isNotEqualTo(UserSyncRules.eventId("u-1", "LOGIN", null, 43));
        assertThat(UserSyncRules.eventId("u-1", null, "UPDATE", 42)).isNotEqualTo(UserSyncRules.eventId("u-1", null, "DELETE", 42));
    }

    @Test
    @DisplayName("R2 · delete wins over login, login over sync; an event nothing syncs asks for nothing")
    void kinds() {
        assertThat(UserSyncRules.kindOf("DELETE", null)).contains(Kind.DELETE);
        assertThat(UserSyncRules.kindOf("ADMIN", "ADMIN_DELETE")).contains(Kind.DELETE);
        assertThat(UserSyncRules.kindOf("LOGIN", null)).contains(Kind.LOGIN);
        for (String event : new String[]{"REGISTER", "UPDATE_PROFILE", "UPDATE_EMAIL", "VERIFY_EMAIL"}) {
            assertThat(UserSyncRules.kindOf(event, null)).as(event).contains(Kind.SYNC);
        }
        assertThat(UserSyncRules.kindOf("ADMIN", "UPDATE")).contains(Kind.SYNC);
        assertThat(UserSyncRules.kindOf("LOGOUT", null)).isEmpty();
    }

    @Test
    @DisplayName("CONTRACT 4.6 · the slim event: ADMIN_* types, the listener's own event id, and the two realms")
    void slimEvent() {
        assertThat(UserSyncRules.kindOf("ADMIN_DELETE")).contains(Kind.DELETE);
        assertThat(UserSyncRules.kindOf("DELETE")).contains(Kind.DELETE);
        assertThat(UserSyncRules.kindOf("ADMIN_UPDATE")).contains(Kind.SYNC);
        assertThat(UserSyncRules.kindOf("ADMIN_CREATE")).contains(Kind.SYNC);
        assertThat(UserSyncRules.kindOf("ADMIN_ACTION")).isEmpty();
        assertThat(UserSyncRules.listenerEventId("evt-9", "u-1", "LOGIN", 0)).isEqualTo("keycloak:evt-9");
        assertThat(UserSyncRules.listenerEventId(" ", "u-1", "LOGIN", 42)).isEqualTo(UserSyncRules.eventId("u-1", "LOGIN", null, 42));
        assertThat(UserSyncRules.knownRealm(null, "myticketzm", "myticketzm-admin")).isTrue();
        assertThat(UserSyncRules.knownRealm("myticketzm-admin", "myticketzm", "myticketzm-admin")).isTrue();
        assertThat(UserSyncRules.knownRealm("master", "myticketzm", "myticketzm-admin")).as("master is not synced").isFalse();
    }

    @Test
    @DisplayName("a registration is REGISTER or an administrator's create")
    void registration() {
        assertThat(UserSyncRules.registration("REGISTER", null)).isTrue();
        assertThat(UserSyncRules.registration("ADMIN", "ADMIN_CREATE")).isTrue();
        assertThat(UserSyncRules.registration("LOGIN", null)).isFalse();
    }
}
