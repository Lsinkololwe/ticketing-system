package com.pml.identity.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Social media links for an organizer profile.
 * Embedded document - not stored as a separate collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialLinks {

    private String facebook;
    private String twitter;
    private String instagram;
    private String linkedin;
    private String youtube;
    private String tiktok;
}
