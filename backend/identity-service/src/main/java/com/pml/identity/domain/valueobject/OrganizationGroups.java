package com.pml.identity.domain.valueobject;


import java.util.List;
import java.util.Locale;

/**
 * The Keycloak group a role is mirrored into.
 *
 * <p>The tree under {@code /organizations/{slug}} is created with plural subgroup names. Every
 * mirror write names its group through {@link #of(OrganizationRole)}, so a write and the tree it
 * lands in cannot disagree on spelling — a write to a group that does not exist succeeds as a no-op
 * at Keycloak and leaves the mirror silently behind.
 */
public final class OrganizationGroups {

    /** The subgroups every organization's tree holds, one per role. */
    public static final List<String> TREE = List.of("owners", "admins", "managers", "marketers", "contributors");

    public static final String OWNERS = "owners";
    public static final String ADMINS = "admins";

    private OrganizationGroups() {
    }

    public static String of(OrganizationRole role) {
        return role.name().toLowerCase(Locale.ROOT) + "s";
    }
}
