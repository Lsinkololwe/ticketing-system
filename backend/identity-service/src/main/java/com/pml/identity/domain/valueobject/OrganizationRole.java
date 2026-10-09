package com.pml.identity.domain.valueobject;

import com.pml.shared.security.Permission;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.pml.shared.security.Permission.ANALYTICS_VIEW;
import static com.pml.shared.security.Permission.ATTENDEE_VIEW;
import static com.pml.shared.security.Permission.BANK_MANAGE;
import static com.pml.shared.security.Permission.EVENT_ACCESS_GRANT;
import static com.pml.shared.security.Permission.EVENT_CANCEL;
import static com.pml.shared.security.Permission.EVENT_CREATE;
import static com.pml.shared.security.Permission.EVENT_DELETE;
import static com.pml.shared.security.Permission.EVENT_EDIT;
import static com.pml.shared.security.Permission.EVENT_PUBLISH;
import static com.pml.shared.security.Permission.EVENT_VIEW;
import static com.pml.shared.security.Permission.FINANCIAL_VIEW;
import static com.pml.shared.security.Permission.ORGANIZATION_BILLING;
import static com.pml.shared.security.Permission.ORGANIZATION_DELETE;
import static com.pml.shared.security.Permission.ORGANIZATION_EDIT;
import static com.pml.shared.security.Permission.ORGANIZATION_TRANSFER;
import static com.pml.shared.security.Permission.ORGANIZATION_VIEW;
import static com.pml.shared.security.Permission.PAYOUT_REQUEST;
import static com.pml.shared.security.Permission.PROMOTION_MANAGE;
import static com.pml.shared.security.Permission.TEAM_INVITE;
import static com.pml.shared.security.Permission.TEAM_REMOVE;
import static com.pml.shared.security.Permission.TEAM_ROLE;
import static com.pml.shared.security.Permission.TEAM_VIEW;
import static com.pml.shared.security.Permission.TICKET_REFUND;
import static com.pml.shared.security.Permission.TICKET_SCAN;

/**
 * A member's role within an organization and the permissions it carries.
 *
 * <p>Each role names only what it adds and which roles it inherits from; {@link #permissions()}
 * is the transitive closure, computed once. Parents form a graph rather than a chain: ADMIN
 * inherits from both MANAGER and MARKETER, and neither of those includes the other, so a question
 * like "is this role senior to that one" has no answer — ask {@link #includes} or
 * {@link #grants} instead.
 *
 * <p>Two permissions depend on the organization's settings rather than the role alone. A MANAGER
 * sees financial figures only when {@link OrganizationSettings#isManagersCanViewFinancials()} is on,
 * and an ADMIN requests payouts only when {@link OrganizationSettings#isAdminsCanRequestPayouts()}
 * is on. They are kept out of the closure and added by {@link #permissions(OrganizationSettings)},
 * so anything that asks without the settings is refused them rather than granted them.
 */
public enum OrganizationRole {

    CONTRIBUTOR(EnumSet.of(EVENT_VIEW, ATTENDEE_VIEW, TICKET_SCAN, ORGANIZATION_VIEW, TEAM_VIEW)),

    MARKETER(EnumSet.of(ANALYTICS_VIEW, PROMOTION_MANAGE), CONTRIBUTOR),

    MANAGER(EnumSet.of(EVENT_CREATE, EVENT_EDIT, EVENT_PUBLISH, ANALYTICS_VIEW, PROMOTION_MANAGE), CONTRIBUTOR),

    ADMIN(EnumSet.of(EVENT_DELETE, EVENT_CANCEL, TICKET_REFUND, TEAM_INVITE, TEAM_REMOVE, TEAM_ROLE,
            EVENT_ACCESS_GRANT, ORGANIZATION_EDIT, BANK_MANAGE, FINANCIAL_VIEW), MANAGER, MARKETER),

    OWNER(EnumSet.of(ORGANIZATION_BILLING, ORGANIZATION_TRANSFER, ORGANIZATION_DELETE, PAYOUT_REQUEST), ADMIN);

    private final Set<Permission> declaredPermissions;
    private final Set<OrganizationRole> parents;

    OrganizationRole(Set<Permission> declaredPermissions, OrganizationRole... parents) {
        this.declaredPermissions = Collections.unmodifiableSet(EnumSet.copyOf(declaredPermissions));
        this.parents = parents.length == 0 ? Set.of() : Set.of(parents);
    }

    public Set<OrganizationRole> parents() {
        return parents;
    }

    /** What the role carries in every organization, whatever its settings. */
    public Set<Permission> permissions() {
        return Closure.BY_ROLE.get(this);
    }

    /** What the role carries in an organization with {@code settings}; {@code null} means every switch off. */
    public Set<Permission> permissions(OrganizationSettings settings) {
        Permission switched = switchedOn(settings);
        if (switched == null) {
            return permissions();
        }
        EnumSet<Permission> withSwitch = EnumSet.copyOf(permissions());
        withSwitch.add(switched);
        return Collections.unmodifiableSet(withSwitch);
    }

    public boolean grants(Permission permission, OrganizationSettings settings) {
        return permission != null && permissions(settings).contains(permission);
    }

    /** True when this role carries everything {@code other} carries, settings aside. */
    public boolean includes(OrganizationRole other) {
        return permissions().containsAll(other.permissions());
    }

    /** The one permission an owner's switch adds to this role, or null when the switch is off. */
    private Permission switchedOn(OrganizationSettings settings) {
        if (settings == null) {
            return null;
        }
        return switch (this) {
            case MANAGER -> settings.isManagersCanViewFinancials() ? FINANCIAL_VIEW : null;
            case ADMIN -> settings.isAdminsCanRequestPayouts() ? PAYOUT_REQUEST : null;
            default -> null;
        };
    }

    private static final class Closure {
        private static final Map<OrganizationRole, Set<Permission>> BY_ROLE = build();

        private static Map<OrganizationRole, Set<Permission>> build() {
            Map<OrganizationRole, Set<Permission>> closures = new EnumMap<>(OrganizationRole.class);
            for (OrganizationRole role : values()) {
                EnumSet<Permission> effective = EnumSet.noneOf(Permission.class);
                Deque<OrganizationRole> pending = new ArrayDeque<>();
                pending.push(role);
                Set<OrganizationRole> seen = EnumSet.noneOf(OrganizationRole.class);
                while (!pending.isEmpty()) {
                    OrganizationRole current = pending.pop();
                    // ADMIN reaches CONTRIBUTOR through both MANAGER and MARKETER; the seen set
                    // visits it once, and would also stop a cycle from looping forever.
                    if (!seen.add(current)) {
                        continue;
                    }
                    effective.addAll(current.declaredPermissions);
                    current.parents.forEach(pending::push);
                }
                closures.put(role, Collections.unmodifiableSet(effective));
            }
            return Collections.unmodifiableMap(closures);
        }
    }
}
