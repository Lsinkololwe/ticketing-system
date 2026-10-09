package com.pml.shared.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every permission the platform checks, in {@code module:action} form.
 *
 * <p>The list is closed: a name that is not here grants nothing, and there is no wildcard.
 * Services exchange permissions by {@link #code()}; the enum constant is only the Java handle.
 * Each code has exactly one colon, so a code splits unambiguously into its module and action.
 *
 * <p>The platform-role sets live here as well, because a platform role's reach is the same in every
 * service. Organization and event roles are resolved by the identity service, which owns
 * memberships and grants.
 */
public enum Permission {

    EVENT_VIEW("event:view", Scope.EVENT, "See an event's details and sales data"),
    EVENT_CREATE("event:create", Scope.ORGANIZATION, "Create a draft event"),
    EVENT_EDIT("event:edit", Scope.EVENT, "Edit an event's details and ticket tiers"),
    EVENT_PUBLISH("event:publish", Scope.EVENT, "Publish, unpublish or reschedule an event"),
    EVENT_DELETE("event:delete", Scope.EVENT, "Delete a draft event"),
    EVENT_CANCEL("event:cancel", Scope.EVENT, "Cancel a published event"),
    ATTENDEE_VIEW("attendee:view", Scope.EVENT, "See the attendee list"),
    TICKET_SCAN("ticket:scan", Scope.EVENT, "Validate tickets at the gate"),
    TICKET_REFUND("ticket:refund", Scope.EVENT, "Issue a refund for a ticket"),
    ANALYTICS_VIEW("analytics:view", Scope.EVENT, "See event and sales analytics"),
    PROMOTION_MANAGE("promotion:manage", Scope.ORGANIZATION, "Create and manage promo codes"),
    FINANCIAL_VIEW("financial:view", Scope.ORGANIZATION, "See revenue, escrow and commission figures"),
    PAYOUT_REQUEST("payout:request", Scope.ORGANIZATION, "Request a payout"),
    PAYOUT_APPROVE("payout:approve", Scope.PLATFORM, "Approve a payout"),
    BANK_MANAGE("bank:manage", Scope.ORGANIZATION, "Add and verify bank accounts"),
    TEAM_INVITE("team:invite", Scope.ORGANIZATION, "Invite a team member"),
    TEAM_REMOVE("team:remove", Scope.ORGANIZATION, "Remove a team member"),
    TEAM_ROLE("team:role", Scope.ORGANIZATION, "Change a team member's role"),
    TEAM_VIEW("team:view", Scope.ORGANIZATION, "See the team list"),
    EVENT_ACCESS_GRANT("event_access:grant", Scope.EVENT, "Grant a person access to a single event"),
    ORGANIZATION_VIEW("organization:view", Scope.ORGANIZATION, "See the organization's profile"),
    ORGANIZATION_EDIT("organization:edit", Scope.ORGANIZATION, "Edit the organization's profile and settings"),
    ORGANIZATION_BILLING("organization:billing", Scope.ORGANIZATION, "Manage billing settings"),
    ORGANIZATION_TRANSFER("organization:transfer", Scope.ORGANIZATION, "Transfer ownership"),
    ORGANIZATION_DELETE("organization:delete", Scope.ORGANIZATION, "Request deletion of the organization"),
    ORGANIZATION_SUSPEND("organization:suspend", Scope.PLATFORM, "Suspend an organization"),
    ORGANIZATION_APPROVE("organization:approve", Scope.PLATFORM, "Approve or reject an organization's application"),
    PLATFORM_CONFIGURE("platform:configure", Scope.PLATFORM, "Change platform configuration and feature flags"),
    TRANSACTION_RECOVER("transaction:recover", Scope.PLATFORM, "Resume, retry and resolve stuck transactions"),
    AUDIT_VIEW("audit:view", Scope.PLATFORM, "Read the audit trail");

    /** Where a permission is held: on the platform, in an organization, or on one event. */
    public enum Scope { PLATFORM, ORGANIZATION, EVENT }

    private static final Map<String, Permission> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Permission::code, Function.identity()));

    private static final Set<Permission> FINANCE_SET = Collections.unmodifiableSet(EnumSet.of(
            FINANCIAL_VIEW, PAYOUT_APPROVE, TICKET_REFUND, TRANSACTION_RECOVER, AUDIT_VIEW,
            ORGANIZATION_VIEW, EVENT_VIEW));

    private final String code;
    private final Scope scope;
    private final String description;

    Permission(String code, Scope scope, String description) {
        this.code = code;
        this.scope = scope;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public Scope scope() {
        return scope;
    }

    public String description() {
        return description;
    }

    /** The part of the code before the colon — {@code event} for {@code event:edit}. */
    public String module() {
        return code.substring(0, code.indexOf(':'));
    }

    /** The permission a code names, or empty when the code is not in the catalogue. */
    public static Optional<Permission> fromCode(String code) {
        return Optional.ofNullable(code == null ? null : BY_CODE.get(code));
    }

    /** The codes of {@code permissions}, sorted so the output is stable. */
    public static Set<String> codes(Collection<Permission> permissions) {
        return permissions.stream().map(Permission::code)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    /**
     * What platform roles grant, before any organization membership or event grant is read.
     * {@code SUPER_ADMIN} holds everything; {@code ADMIN} everything except platform
     * configuration; {@code FINANCE} a money-and-audit set that cannot touch an organization's
     * events or team. Every other role — {@code ORGANIZER}, {@code CUSTOMER}, {@code FINANCE_LEAD} —
     * grants nothing on its own. Role names are compared without case and without a
     * {@code ROLE_} prefix, so realm roles and Spring authorities both work.
     */
    public static Set<Permission> grantedByPlatformRoles(Collection<String> roles) {
        Set<String> held = roles.stream()
                .filter(java.util.Objects::nonNull)
                .map(role -> role.toUpperCase(java.util.Locale.ROOT))
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .collect(Collectors.toSet());
        EnumSet<Permission> granted = EnumSet.noneOf(Permission.class);
        if (held.contains("SUPER_ADMIN")) {
            granted.addAll(EnumSet.allOf(Permission.class));
        } else if (held.contains("ADMIN")) {
            granted.addAll(EnumSet.complementOf(EnumSet.of(PLATFORM_CONFIGURE)));
        }
        if (held.contains("FINANCE")) {
            granted.addAll(FINANCE_SET);
        }
        return Collections.unmodifiableSet(granted);
    }
}
