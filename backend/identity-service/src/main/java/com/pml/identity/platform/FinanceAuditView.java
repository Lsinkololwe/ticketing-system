package com.pml.identity.platform;

import com.pml.shared.security.tenancy.PlatformWideAccess.Reason;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the finance officer's view of the audit trail contains.
 *
 * <p>Finance audits money. So the view is every recorded platform-wide reach whose reason is about
 * money, by anyone: an administrator who reads a bank account or cancels a refund request must be
 * visible to the finance auditor exactly as a finance officer is. The set is closed and decided here,
 * on the server, from the caller's role; nothing a client sends can widen it. A reason that is not in
 * the set is never returned to finance, whatever filter is asked for.
 *
 * <h2>In the set, and why</h2>
 * <ul>
 *   <li>{@code BANK_ACCOUNT_MANAGE}, {@code BANK_ACCOUNTS_READ}, {@code DEFAULT_BANK_ACCOUNT_READ}:
 *       where an organizer's money is paid out. Changing or reading it is the first thing a payout
 *       fraud touches.</li>
 *   <li>{@code REFUND_REQUEST_CANCEL}: staff withdrawing a buyer's refund request decides whether
 *       money goes back.</li>
 *   <li>{@code BUYER_BOOKINGS_READ}, {@code RESERVATION_SUPPORT_READ}: staff reading a buyer's
 *       bookings and reservations in order to support refunds, disputes and reconciliation; the rows
 *       carry amounts paid and payment state.</li>
 *   <li>{@code PLATFORM_SALES_ANALYTICS_READ}: platform-wide revenue and sales figures.</li>
 *   <li>{@code RECOVERY_ACTION_PROPOSE}, {@code RECOVERY_ACTION_CONFIRM}: the two-person finance
 *       recovery (write-offs, transfers), the clearest money movement an administrator can make.</li>
 * </ul>
 *
 * <h2>Left out, and why</h2>
 * <ul>
 *   <li>{@code AUDIT_TRAIL_READ} and {@code FINANCE_AUDIT_VIEW_READ}: who read the audit is a
 *       compliance matter for administrators, not a money event.</li>
 *   <li>{@code BOOKING_CONTACT_READ}: a buyer's contact details, personal data and not money.</li>
 *   <li>{@code EVENT_ACCESS_GRANTS_READ}, {@code USER_EVENT_GRANT_READ}, {@code ORGANIZATION_MEMBER_READ},
 *       {@code ORGANIZATION_TEAM_READ}, {@code OWNERSHIP_TRANSFER_READ}: user, team and organization
 *       management.</li>
 *   <li>{@code CALLER_SCOPED_QUERY}: a generic "an administrator listed without naming an
 *       organization", with no indication that money was involved.</li>
 *   <li>{@code STAFF_PERMISSION_GRANT_READ}: a role carrying a permission on an organizer's data; it
 *       spans attendees, events and gate work as much as money, so it would bring in rows that are not
 *       money.</li>
 *   <li>{@code PROMO_CODE_MANAGE}: staff creating or changing an organization's promo codes; a
 *       commercial setting, and the discount it grants shows in the sale it applies to, not here.</li>
 *   <li>{@code LIFECYCLE_WORKFLOW}, {@code EVENT_COMPLETION}, {@code FINANCE_WORKFLOW},
 *       {@code REFUND_WORKFLOW}: system reaches, which are written to the service log and never as
 *       audit rows, so there is nothing to show.</li>
 * </ul>
 *
 * <p>Every {@link Reason} must be named in exactly one of the two lists above: a reason added later
 * fails {@code FinanceAuditViewTest} until someone decides which side it is on.
 */
public final class FinanceAuditView {

    /** The reasons whose rows finance may read, whoever the actor. */
    public static final Set<Reason> MONEY_REASONS = Set.copyOf(EnumSet.of(
            Reason.BANK_ACCOUNT_MANAGE,
            Reason.BANK_ACCOUNTS_READ,
            Reason.DEFAULT_BANK_ACCOUNT_READ,
            Reason.REFUND_REQUEST_CANCEL,
            Reason.BUYER_BOOKINGS_READ,
            Reason.RESERVATION_SUPPORT_READ,
            Reason.PLATFORM_SALES_ANALYTICS_READ,
            Reason.RECOVERY_ACTION_PROPOSE,
            Reason.RECOVERY_ACTION_CONFIRM));

    /** The same set as the names stored on audit rows. */
    public static final Set<String> MONEY_REASON_NAMES =
            MONEY_REASONS.stream().map(Reason::name).collect(Collectors.toUnmodifiableSet());

    private FinanceAuditView() {
    }
}
