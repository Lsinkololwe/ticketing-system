package com.pml.identity.organization;

import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.shared.security.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static com.pml.shared.security.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;

/** The event role table and what an event grant confers. Pure values. */
@Tag("L1")
@Tag("ET-ORG-003")
@DisplayName("Event roles carry exactly their permissions, and a grant adds only catalogue codes")
class EventRoleTest {

    @Test
    @DisplayName("Each event role carries exactly its row")
    void eachRoleCarriesItsRow() {
        Set<Permission> owner = EnumSet.of(EVENT_VIEW, EVENT_EDIT, EVENT_PUBLISH, EVENT_CANCEL, EVENT_DELETE,
                ATTENDEE_VIEW, TICKET_SCAN, TICKET_REFUND, ANALYTICS_VIEW, EVENT_ACCESS_GRANT);
        Set<Permission> admin = EnumSet.copyOf(owner);
        admin.removeAll(EnumSet.of(EVENT_CANCEL, EVENT_DELETE));

        assertThat(EventRole.EVENT_OWNER.permissions()).containsExactlyInAnyOrderElementsOf(owner);
        assertThat(EventRole.EVENT_ADMIN.permissions()).containsExactlyInAnyOrderElementsOf(admin);
        assertThat(EventRole.EDITOR.permissions()).containsExactlyInAnyOrder(EVENT_VIEW, EVENT_EDIT, ATTENDEE_VIEW, TICKET_SCAN, ANALYTICS_VIEW);
        assertThat(EventRole.CHECK_IN.permissions()).containsExactlyInAnyOrder(EVENT_VIEW, ATTENDEE_VIEW, TICKET_SCAN);
        assertThat(EventRole.VIEWER.permissions()).containsExactlyInAnyOrder(EVENT_VIEW, ANALYTICS_VIEW);
    }

    @Test
    @DisplayName("No event role carries an organization-wide or platform permission")
    void eventRolesStayOnTheEvent() {
        for (EventRole role : EventRole.values()) {
            assertThat(role.permissions()).allMatch(permission -> permission.scope() == Permission.Scope.EVENT, role.name());
        }
    }

    @Test
    @DisplayName("A grant confers its role's permissions plus known custom codes, and nothing for an unknown name")
    void grantPermissions() {
        EventAccessGrant grant = new EventAccessGrant();
        grant.setEventRole(EventRole.CHECK_IN);
        grant.setCustomPermissions(Set.of("analytics:view", "EVENT_EDIT", "event:bogus"));

        assertThat(grant.hasPermission(ANALYTICS_VIEW)).isTrue();
        assertThat(grant.hasPermission(TICKET_SCAN)).isTrue();
        assertThat(grant.hasPermission(EVENT_EDIT)).as("an old upper-case name grants nothing").isFalse();
        assertThat(grant.hasPermission(null)).isFalse();
        assertThat(grant.permissions()).containsExactlyInAnyOrder(EVENT_VIEW, ATTENDEE_VIEW, TICKET_SCAN, ANALYTICS_VIEW);
    }
}
