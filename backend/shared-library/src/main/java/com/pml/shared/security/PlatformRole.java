package com.pml.shared.security;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The roles a person holds on the platform itself, as opposed to inside one organization, with
 * the two things the platform asks of them: which of one another they stand in for, and which
 * permissions they grant before any membership or event grant is read.
 *
 * <p>Every rule that compares platform roles goes through this type, so the schema's {@code @auth}
 * directive, the staff rank rule and the permission catalogue cannot disagree.
 */
public enum PlatformRole {

    SUPER_ADMIN,
    ADMIN,
    FINANCE,
    /** Receives chargeback and refund escalations; its holder also holds {@code FINANCE}. */
    FINANCE_LEAD,
    ORGANIZER,
    CUSTOMER;

    /** The Spring authority, for example {@code ROLE_FINANCE}. */
    public String authority() {
        return Authorities.role(name());
    }

    /** The role a name or authority denotes, or empty for anything that is not a platform role. */
    public static Optional<PlatformRole> fromName(String authorityOrName) {
        if (authorityOrName == null || authorityOrName.isBlank()) {
            return Optional.empty();
        }
        String name = Authorities.roleName(authorityOrName);
        for (PlatformRole role : values()) {
            if (role.name().equals(name)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }

    /** The platform roles among {@code names}; anything unrecognised is ignored. */
    public static Set<PlatformRole> heldBy(Collection<String> names) {
        EnumSet<PlatformRole> held = EnumSet.noneOf(PlatformRole.class);
        if (names != null) {
            names.stream().filter(Objects::nonNull).map(PlatformRole::fromName)
                    .flatMap(Optional::stream).forEach(held::add);
        }
        return held;
    }

    /**
     * The platform roles among exact Spring authorities. Unlike {@link #heldBy}, a bare name or a
     * different case does not count: this is what an authorization gate reads, and a gate must not
     * be wider than what the token carried.
     */
    public static Set<PlatformRole> heldByAuthority(Collection<String> authorities) {
        EnumSet<PlatformRole> held = EnumSet.noneOf(PlatformRole.class);
        if (authorities != null) {
            for (PlatformRole role : values()) {
                if (authorities.contains(role.authority())) {
                    held.add(role);
                }
            }
        }
        return held;
    }

    /**
     * Whether holding this role satisfies a requirement for {@code required}. A super
     * administrator satisfies everything; an administrator also stands in for finance, organizers
     * and customers; an organizer stands in for a customer; every other role satisfies itself.
     */
    public boolean satisfies(PlatformRole required) {
        if (this == required || this == SUPER_ADMIN) {
            return true;
        }
        return switch (this) {
            case ADMIN -> required == FINANCE || required == ORGANIZER || required == CUSTOMER;
            case ORGANIZER -> required == CUSTOMER;
            default -> false;
        };
    }

    /** Whether any of {@code held} satisfies {@code required}. */
    public static boolean anySatisfies(Collection<PlatformRole> held, PlatformRole required) {
        return held.stream().anyMatch(role -> role.satisfies(required));
    }

    /**
     * The permissions this role grants on its own. A super administrator holds everything and an
     * administrator everything except platform configuration, forcing a payment, a staff member's
     * temporary password and processing a refund; finance holds a money-and-audit set
     * that reaches no organization's events or team. Organizers, customers and the finance lead
     * grant nothing: an organizer's access comes entirely from membership.
     */
    public Set<Permission> grants() {
        return switch (this) {
            case SUPER_ADMIN -> EnumSet.allOf(Permission.class);
            case ADMIN -> EnumSet.complementOf(EnumSet.of(
                    Permission.PLATFORM_CONFIGURE, Permission.PAYMENT_APPROVE,
                    Permission.STAFF_MANAGE, Permission.REFUND_APPROVE));
            case FINANCE -> EnumSet.of(
                    Permission.FINANCIAL_VIEW, Permission.PAYOUT_APPROVE, Permission.TICKET_REFUND,
                    Permission.TRANSACTION_RECOVER, Permission.AUDIT_VIEW, Permission.ORGANIZATION_VIEW,
                    Permission.EVENT_VIEW,
                    Permission.LEDGER_VIEW,
                    Permission.RECONCILIATION_VIEW,
                    Permission.ESCROW_VIEW,
                    Permission.CHARGEBACK_VIEW,
                    Permission.CHARGEBACK_RECOVER,
                    Permission.PAYMENT_VIEW,
                    Permission.PAYOUT_VIEW,
                    Permission.PAYOUT_MANAGE,
                    Permission.REFUND_VIEW,
                    Permission.REFUND_MANAGE,
                    Permission.REFUND_APPROVE,
                    Permission.COMMISSION_VIEW,
                    Permission.SETTLEMENT_VIEW,
                    Permission.PLATFORM_ACCOUNT_TRANSFER,
                    Permission.STATISTICS_VIEW,
                    Permission.REPORT_CREATE,
                    Permission.BANK_ACCOUNT_VIEW,
                    Permission.BANK_ACCOUNT_APPROVE,
                    Permission.RESERVATION_VIEW,
                    Permission.TICKET_VIEW,
                    Permission.BUYER_VIEW);
            case FINANCE_LEAD, ORGANIZER, CUSTOMER -> EnumSet.noneOf(Permission.class);
        };
    }

    /** The union of what {@code names} grant, as an unmodifiable set. */
    public static Set<Permission> grantedBy(Collection<String> names) {
        EnumSet<Permission> granted = EnumSet.noneOf(Permission.class);
        heldBy(names).forEach(role -> granted.addAll(role.grants()));
        return Collections.unmodifiableSet(granted);
    }
}
