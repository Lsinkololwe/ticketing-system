package com.pml.shared.security.tenancy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Set;

/**
 * {@code platform.tenancy.*} — who reads organization-owned records across every organization.
 *
 * @param platformWideAuthorities authorities whose holders are scoped platform-wide rather than to
 *                                their own organizations; administrators everywhere, and whatever a
 *                                service adds for its own staff
 * @param identityUrl             identity-service's base URL, where a service other than identity
 *                                asks for a caller's memberships; unset in identity itself
 */
@ConfigurationProperties(prefix = "platform.tenancy")
public record TenancyProperties(
        @DefaultValue({"ROLE_ADMIN", "ROLE_SUPER_ADMIN"}) Set<String> platformWideAuthorities,
        String identityUrl) {
}
