package com.pml.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User Summary DTO
 *
 * Lightweight representation of a user for inter-service communication.
 * Used when services need basic user information without full user details.
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
    private String userType;
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
}
