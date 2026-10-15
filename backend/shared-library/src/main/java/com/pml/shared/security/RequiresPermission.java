package com.pml.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires the caller's platform roles to grant {@link #value()}. It states in the code which
 * catalogue permission an operation is, and refuses anyone whose roles do not carry it, whatever
 * else the operation's role gate admits.
 *
 * <p>It is for operations that belong to the platform itself (approving an organization, approving a
 * payout, recovering a transaction, reading the audit trail); organization and event permissions are
 * decided by identity against a membership or a grant. It runs after {@code @PreAuthorize}, so it
 * narrows an operation and never widens one. The method must return a {@code Mono} or a {@code Flux}.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /** The platform permission the caller's roles must grant. */
    Permission value();

    /**
     * Also admits a trusted service calling with the internal write scope. For the few operations a
     * workflow or another service performs on the platform's behalf (completing an event once its
     * date has passed); a person is still refused unless a platform role grants {@link #value()}.
     */
    boolean orInternalService() default false;
}
