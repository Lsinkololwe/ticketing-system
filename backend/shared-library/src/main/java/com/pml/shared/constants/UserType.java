package com.pml.shared.constants;

import java.util.EnumSet;
import java.util.Set;

/**
 * User Type Enum
 *
 * Defines the different types/roles of users in the system.
 *
 * IMPORTANT: Users can have MULTIPLE roles. Use Set<UserType> to represent
 * user roles. The CUSTOMER role is the base role that cannot be removed.
 *
 * @see #getDefaultRoles() for the default role set
 * @see #isValidRoleCombination(Set) for validation rules
 */
public enum UserType {

    CUSTOMER("CUSTOMER", "Customer", "Regular ticket buyer"),
    ORGANIZER("ORGANIZER", "Organizer", "Event organizer who can create and manage events"),
    ADMIN("ADMIN", "Administrator", "Platform administrator with full access"),
    SUPER_ADMIN("SUPER_ADMIN", "Super Administrator", "Super administrator with highest privileges"),
    SCANNER("SCANNER", "Scanner", "Ticket scanner/validator at events"),
    FINANCE("FINANCE", "Finance", "Finance team member for payouts and reconciliation");

    private final String code;
    private final String displayName;
    private final String description;

    UserType(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static UserType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (UserType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if this role is an admin role.
     *
     * @return true if ADMIN or SUPER_ADMIN
     */
    public boolean isAdmin() {
        return this == ADMIN || this == SUPER_ADMIN;
    }

    /**
     * Check if this role can create events.
     *
     * @return true if ORGANIZER or admin role
     */
    public boolean canCreateEvents() {
        return this == ORGANIZER || isAdmin();
    }

    /**
     * Check if this role can scan tickets.
     *
     * @return true if SCANNER, ORGANIZER, or admin role
     */
    public boolean canScanTickets() {
        return this == SCANNER || this == ORGANIZER || isAdmin();
    }

    /**
     * Check if this role can process payouts.
     *
     * @return true if FINANCE or SUPER_ADMIN
     */
    public boolean canProcessPayouts() {
        return this == FINANCE || this == SUPER_ADMIN;
    }

    // ========================================================================
    // MULTI-ROLE SUPPORT METHODS
    // ========================================================================

    /**
     * Get the default roles for a new user.
     * All users start with at least the CUSTOMER role.
     *
     * @return EnumSet containing the default CUSTOMER role
     */
    public static EnumSet<UserType> getDefaultRoles() {
        return EnumSet.of(CUSTOMER);
    }

    /**
     * Validate that a set of roles is valid according to business rules.
     *
     * Rules:
     * 1. Must have at least one role
     * 2. Must include CUSTOMER (base role cannot be removed)
     * 3. Cannot have both ADMIN and SUPER_ADMIN (SUPER_ADMIN supersedes ADMIN)
     * 4. Maximum 6 roles (all possible roles)
     *
     * @param roles the set of roles to validate
     * @return true if the combination is valid, false otherwise
     */
    public static boolean isValidRoleCombination(Set<UserType> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }

        // Rule: CUSTOMER role is required (base role)
        if (!roles.contains(CUSTOMER)) {
            return false;
        }

        // Rule: Cannot have both ADMIN and SUPER_ADMIN
        if (roles.contains(ADMIN) && roles.contains(SUPER_ADMIN)) {
            return false;
        }

        // Rule: Max 6 roles
        if (roles.size() > 6) {
            return false;
        }

        return true;
    }

    /**
     * Check if a role can be added to an existing set of roles.
     *
     * @param existingRoles the current roles
     * @param roleToAdd the role to add
     * @return true if adding the role would result in a valid combination
     */
    public static boolean canAddRole(Set<UserType> existingRoles, UserType roleToAdd) {
        if (existingRoles == null || roleToAdd == null) {
            return false;
        }

        // Already has the role
        if (existingRoles.contains(roleToAdd)) {
            return false;
        }

        EnumSet<UserType> testSet = EnumSet.copyOf(existingRoles);
        testSet.add(roleToAdd);
        return isValidRoleCombination(testSet);
    }

    /**
     * Check if a role can be removed from an existing set of roles.
     *
     * @param existingRoles the current roles
     * @param roleToRemove the role to remove
     * @return true if removing the role would result in a valid combination
     */
    public static boolean canRemoveRole(Set<UserType> existingRoles, UserType roleToRemove) {
        if (existingRoles == null || roleToRemove == null) {
            return false;
        }

        // Cannot remove CUSTOMER role
        if (roleToRemove == CUSTOMER) {
            return false;
        }

        // Doesn't have the role
        if (!existingRoles.contains(roleToRemove)) {
            return false;
        }

        EnumSet<UserType> testSet = EnumSet.copyOf(existingRoles);
        testSet.remove(roleToRemove);
        return isValidRoleCombination(testSet);
    }

    /**
     * Get the highest privilege role from a set of roles.
     * Order of privilege: SUPER_ADMIN > ADMIN > FINANCE > ORGANIZER > SCANNER > CUSTOMER
     *
     * @param roles the set of roles
     * @return the highest privilege role
     */
    public static UserType getHighestPrivilegeRole(Set<UserType> roles) {
        if (roles == null || roles.isEmpty()) {
            return CUSTOMER;
        }

        if (roles.contains(SUPER_ADMIN)) return SUPER_ADMIN;
        if (roles.contains(ADMIN)) return ADMIN;
        if (roles.contains(FINANCE)) return FINANCE;
        if (roles.contains(ORGANIZER)) return ORGANIZER;
        if (roles.contains(SCANNER)) return SCANNER;
        return CUSTOMER;
    }
}
