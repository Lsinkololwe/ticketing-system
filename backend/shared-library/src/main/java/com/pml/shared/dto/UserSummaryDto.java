package com.pml.shared.dto;

import com.pml.shared.constants.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User Summary DTO
 *
 * Lightweight representation of a user for inter-service communication.
 * Used when services need basic user information without full user details.
 *
 * <h2>Multi-Role Support</h2>
 * <p>Users can have multiple roles (e.g., CUSTOMER + ORGANIZER). The {@code roles}
 * field contains all assigned roles. For backward compatibility, {@code getUserType()}
 * returns the highest privilege role.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDto {

    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;

    /**
     * User's roles. A user can have multiple roles (e.g., CUSTOMER + ORGANIZER).
     * All users have at least the CUSTOMER role.
     */
    @Builder.Default
    private Set<String> roles = new HashSet<>();

    private boolean emailVerified;
    private boolean phoneVerified;
    private boolean active;

    /**
     * Get the user's full name
     */
    public String getFullName() {
        if (firstName == null && lastName == null) {
            return null;
        }
        StringBuilder name = new StringBuilder();
        if (firstName != null) {
            name.append(firstName);
        }
        if (lastName != null) {
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(lastName);
        }
        return name.toString();
    }

    /**
     * Check if the user is verified (email or phone)
     */
    public boolean isVerified() {
        return emailVerified || phoneVerified;
    }

    /**
     * Check if the user has a specific role.
     *
     * @param role the role to check (e.g., "ORGANIZER", "ADMIN")
     * @return true if the user has the role
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /**
     * Check if the user has a specific UserType role.
     *
     * @param userType the UserType to check
     * @return true if the user has the role
     */
    public boolean hasRole(UserType userType) {
        return userType != null && hasRole(userType.name());
    }

    /**
     * Check if the user is an organizer.
     *
     * @return true if user has ORGANIZER role
     */
    public boolean isOrganizer() {
        return hasRole(UserType.ORGANIZER);
    }

    /**
     * Check if the user is an admin.
     *
     * @return true if user has ADMIN or SUPER_ADMIN role
     */
    public boolean isAdmin() {
        return hasRole(UserType.ADMIN) || hasRole(UserType.SUPER_ADMIN);
    }

    /**
     * Get immutable copy of roles.
     *
     * @return unmodifiable set of role names
     */
    public Set<String> getRolesImmutable() {
        return roles != null ? Collections.unmodifiableSet(roles) : Collections.emptySet();
    }
}
