package com.pml.identity.authorization;

import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.shared.security.Permission;

import java.util.EnumSet;
import java.util.Set;

/**
 * The resolution order, written once, with no I/O. Given a {@link DecisionContext} it says whether
 * a permission is allowed and which step said so; every other answer the service gives — whether
 * a check passes, which permissions a person holds in a place — is derived from this method, so
 * the two cannot disagree.
 *
 * <p>The order is the specification:
 * <ol>
 *   <li>a platform role that grants the permission allows;</li>
 *   <li>for an event, an active grant issued by the event's organization decides alone: it allows
 *       through its role or a custom addition and otherwise refuses, without falling through to
 *       the member's role or denials;</li>
 *   <li>otherwise the caller must be an active member of the organization;</li>
 *   <li>the member's denial refuses what the role or a custom addition would give;</li>
 *   <li>the member's custom or role permissions, under the organization's switches, allow, and the
 *       organization's lifecycle status may still refuse them;</li>
 *   <li>anything else refuses.</li>
 * </ol>
 * A platform role never refuses: when it does not grant the permission, resolution continues.
 */
public final class PermissionDecider {

    private PermissionDecider() {
    }

    /** The decision for one permission in the given context. */
    public static Decision decide(Permission permission, DecisionContext context) {
        if (permission == null) {
            return Decision.of(DecisionStep.PERMISSION_UNKNOWN);
        }
        if (context.platformGrants().contains(permission)) {
            return Decision.of(DecisionStep.PLATFORM_ROLE);
        }
        if (context.eventScoped()) {
            EventAccessGrant grant = applicableGrant(context);
            if (grant != null) {
                return decideByGrant(permission, grant, context);
            }
            if (context.organizationId() == null) {
                return Decision.of(DecisionStep.NO_ORGANIZATION);
            }
        }
        return decideByMembership(permission, context);
    }

    /**
     * Every permission the context allows, found by deciding each catalogue permission, so the
     * set is by construction the one {@link #decide} would answer check by check.
     */
    public static Set<Permission> allowed(DecisionContext context) {
        EnumSet<Permission> allowed = EnumSet.noneOf(Permission.class);
        for (Permission permission : Permission.values()) {
            if (decide(permission, context).allowed()) {
                allowed.add(permission);
            }
        }
        return allowed;
    }

    /** Which source of authority is in play for the context, highest precedence first. */
    public static Source source(DecisionContext context) {
        if (!context.platformGrants().isEmpty()) {
            return Source.PLATFORM;
        }
        if (context.eventScoped() && applicableGrant(context) != null) {
            return Source.EVENT;
        }
        if (activeMember(context) != null) {
            return Source.ORGANIZATION;
        }
        return Source.NONE;
    }

    /** Where the authority in a context comes from. */
    public enum Source { PLATFORM, EVENT, ORGANIZATION, NONE }

    /**
     * The grant counts only while it is active and unexpired at {@code now}, and only on an event
     * of the organization that issued it; a grant from another organization is treated as absent.
     */
    static EventAccessGrant applicableGrant(DecisionContext context) {
        EventAccessGrant grant = context.grant();
        if (grant == null || !grant.isValid(context.now())) {
            return null;
        }
        String named = context.organizationId();
        if (named != null && !named.equals(grant.getOrganizationId())) {
            return null;
        }
        return grant;
    }

    static OrganizationMember activeMember(DecisionContext context) {
        OrganizationMember member = context.member();
        return member != null && member.isActive() ? member : null;
    }

    private static Decision decideByGrant(Permission permission, EventAccessGrant grant, DecisionContext context) {
        String role = grant.getEventRole() == null ? null : grant.getEventRole().name();
        boolean byRole = grant.getEventRole() != null && grant.getEventRole().grants(permission);
        boolean custom = grant.getCustomPermissions() != null
                && grant.getCustomPermissions().contains(permission.code());
        if (!byRole && !custom) {
            return new Decision(DecisionStep.GRANT_EXHAUSTIVE, role, null, null);
        }
        // A grant is the organization's delegation, so it is frozen with the organization: a suspended
        // organization's guests cannot act on its events any more than its members can.
        var organization = context.organization();
        if (organization != null && !organization.canPerform(permission)) {
            String status = organization.getStatus() == null ? null : organization.getStatus().name();
            return new Decision(DecisionStep.ORGANIZATION_STATUS, role, organization.getId(), status);
        }
        return new Decision(byRole ? DecisionStep.GRANT_ROLE : DecisionStep.GRANT_CUSTOM, role, null, null);
    }

    private static Decision decideByMembership(Permission permission, DecisionContext context) {
        OrganizationMember member = activeMember(context);
        if (member == null || context.organizationId() == null) {
            return Decision.of(DecisionStep.NOT_A_MEMBER);
        }
        if (context.organization() == null) {
            return Decision.of(DecisionStep.ORGANIZATION_UNKNOWN);
        }
        var organization = context.organization();
        OrganizationSettings settings = organization.getSettings() == null
                ? new OrganizationSettings() : organization.getSettings();
        String role = member.getRole() == null ? null : member.getRole().name();
        String status = organization.getStatus() == null ? null : organization.getStatus().name();

        DecisionStep step;
        if (member.denies(permission)) {
            step = DecisionStep.MEMBER_DENIED;
        } else if (member.carriesCustom(permission)) {
            step = DecisionStep.MEMBER_CUSTOM;
        } else if (member.getRole() != null && member.getRole().grants(permission, settings)) {
            step = DecisionStep.MEMBER_ROLE;
        } else {
            step = DecisionStep.DEFAULT;
        }
        if (step.allows() && !organization.canPerform(permission)) {
            step = DecisionStep.ORGANIZATION_STATUS;
        }
        return new Decision(step, role, organization.getId(), status);
    }
}
