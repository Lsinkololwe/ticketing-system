package com.pml.identity.domain.valueobject;

import com.pml.shared.security.Permission;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static com.pml.shared.security.Permission.ANALYTICS_VIEW;
import static com.pml.shared.security.Permission.ATTENDEE_VIEW;
import static com.pml.shared.security.Permission.EVENT_ACCESS_GRANT;
import static com.pml.shared.security.Permission.EVENT_CANCEL;
import static com.pml.shared.security.Permission.EVENT_DELETE;
import static com.pml.shared.security.Permission.EVENT_EDIT;
import static com.pml.shared.security.Permission.EVENT_PUBLISH;
import static com.pml.shared.security.Permission.EVENT_VIEW;
import static com.pml.shared.security.Permission.TICKET_REFUND;
import static com.pml.shared.security.Permission.TICKET_SCAN;

/**
 * A person's role on one event, given by an event access grant.
 *
 * <p>When someone holds a grant on an event, the grant alone decides what they may do there: their
 * organization role is not consulted for that event, so a grant can narrow a member's reach as well
 * as widen a non-member's.
 */
public enum EventRole {

    /** Created with the event and never revoked: full control, including cancelling and deleting it. */
    EVENT_OWNER(EnumSet.of(EVENT_VIEW, EVENT_EDIT, EVENT_PUBLISH, EVENT_CANCEL, EVENT_DELETE, ATTENDEE_VIEW,
            TICKET_SCAN, TICKET_REFUND, ANALYTICS_VIEW, EVENT_ACCESS_GRANT)),

    /** Everything the owner can do except cancel or delete the event. */
    EVENT_ADMIN(EnumSet.of(EVENT_VIEW, EVENT_EDIT, EVENT_PUBLISH, ATTENDEE_VIEW, TICKET_SCAN, TICKET_REFUND,
            ANALYTICS_VIEW, EVENT_ACCESS_GRANT)),

    /** Edits the event's content and tiers; cannot publish, refund or grant access. */
    EDITOR(EnumSet.of(EVENT_VIEW, EVENT_EDIT, ATTENDEE_VIEW, TICKET_SCAN, ANALYTICS_VIEW)),

    /** Gate staff: scans tickets and sees who is attending. */
    CHECK_IN(EnumSet.of(EVENT_VIEW, ATTENDEE_VIEW, TICKET_SCAN)),

    /** Read-only: the event and its sales figures. */
    VIEWER(EnumSet.of(EVENT_VIEW, ANALYTICS_VIEW));

    private final Set<Permission> permissions;

    EventRole(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean grants(Permission permission) {
        return permission != null && permissions.contains(permission);
    }
}
