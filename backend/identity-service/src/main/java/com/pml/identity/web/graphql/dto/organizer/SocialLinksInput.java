package com.pml.identity.web.graphql.dto.organizer;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Input for social media links.
 *
 * All URLs are validated to be proper HTTP/HTTPS URLs or empty.
 * Maximum length prevents DoS via oversized inputs.
 */
public record SocialLinksInput(
        @Size(max = 500, message = "Facebook URL must not exceed 500 characters")
        @Pattern(regexp = "^(https?://.*)?$", message = "Facebook must be a valid URL")
        String facebook,

        @Size(max = 500, message = "Twitter URL must not exceed 500 characters")
        @Pattern(regexp = "^(https?://.*)?$", message = "Twitter must be a valid URL")
        String twitter,

        @Size(max = 500, message = "Instagram URL must not exceed 500 characters")
        @Pattern(regexp = "^(https?://.*)?$", message = "Instagram must be a valid URL")
        String instagram,

        @Size(max = 500, message = "LinkedIn URL must not exceed 500 characters")
        @Pattern(regexp = "^(https?://.*)?$", message = "LinkedIn must be a valid URL")
        String linkedin,

        @Size(max = 500, message = "YouTube URL must not exceed 500 characters")
        @Pattern(regexp = "^(https?://.*)?$", message = "YouTube must be a valid URL")
        String youtube,

        @Size(max = 500, message = "TikTok URL must not exceed 500 characters")
        @Pattern(regexp = "^(https?://.*)?$", message = "TikTok must be a valid URL")
        String tiktok
) {}
