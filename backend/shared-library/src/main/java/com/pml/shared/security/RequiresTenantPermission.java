package com.pml.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An operation that belongs to an organization's own work, guarded by the catalogue permission it is.
 *
 * <p>The operation is admitted for the caller who holds {@link #value()} on the platform (staff) or who
 * belongs to at least one organization. That is the cheap, uniform front gate. The decision that matters
 * is the second one, and it is the service's: the organization or event the call names is resolved and
 * {@link #value()} is checked for the caller against it (through the tenant guard and identity's
 * permission resolution), so a member of one organization can never act in another. The annotation
 * therefore states which permission the operation <em>is</em>, and a lint keeps it to catalogue
 * permissions.
 *
 * <p>It is the replacement for {@code hasRole('ORGANIZER')}-style gates, which asked who the caller is
 * and said nothing about what the operation requires. The method must return a {@code Mono} or a
 * {@code Flux}.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresTenantPermission {

    /** The permission the operation is, held platform-wide or in the target organization or event. */
    Permission value();
}
