package com.pml.identity.account;

import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.User;
import com.pml.identity.security.revocation.AccessCutter;
import com.pml.identity.security.revocation.RevocationTrigger;
import com.pml.identity.service.AdminAuditService;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Issues a platform staff member a new one-time password, on a super administrator's word.
 *
 * <p>The staff realm has no self-service password reset, so a person who lost the password they were
 * given, or who is locked out, goes back to the super administrator. This is that path: the account is
 * checked (a staff account, one that has a sign-in), the sessions it holds now are revoked and ended so
 * nobody keeps access that rested on the old password, a new random password is set as a temporary
 * credential (Keycloak then makes the person choose their own, and ask for a second factor if none is
 * enrolled), the sessions are ended once more to catch one opened in between, and a row records who
 * reset whom.</p>
 *
 * <p>The password is returned to the caller and goes nowhere else: it is not stored, not logged, not in
 * the audit row. Revocation is of the sessions that exist now, not of the person's subject, so the
 * person can sign in with the new password straight away.</p>
 */
@Service
public class StaffPasswordReset {

    private static final Logger log = LoggerFactory.getLogger(StaffPasswordReset.class);

    private final AccountService accounts;
    private final KeycloakUserAdminPort keycloak;
    private final TemporaryPasswordGenerator passwords;
    private final AccessCutter access;
    private final AdminAuditService audit;

    public StaffPasswordReset(AccountService accounts, KeycloakUserAdminPort keycloak,
                              TemporaryPasswordGenerator passwords, AccessCutter access, AdminAuditService audit) {
        this.accounts = accounts;
        this.keycloak = keycloak;
        this.passwords = passwords;
        this.access = access;
        this.audit = audit;
    }

    /**
     * @param accountId the staff account whose password is replaced
     * @param actorId   the super administrator asking, recorded on the audit row
     * @return the account and the new password, which is not retrievable again
     */
    public Mono<StaffAccountPassword> reset(String accountId, String actorId) {
        // Checked here as well as at the resolver: the operation touches a staff member whatever their role.
        return StaffRolePolicy.requireSuperAdminToTouch(List.of(UserType.ADMIN))
                .then(Mono.defer(() -> accounts.require(accountId)))
                .flatMap(account -> {
                    refuseUnlessStaffWithSignIn(account);
                    String realm = accounts.realmOf(account);
                    String keycloakUserId = account.getKeycloakUserId();
                    String password = passwords.generate();
                    return access.cutSessions(account.getId(), RevocationTrigger.CREDENTIAL_CHANGE, actorId)
                            .then(Mono.defer(() -> keycloak.issueTemporaryPassword(keycloakUserId, password)))
                            .then(Mono.defer(() -> keycloak.endSessions(realm, keycloakUserId)))
                            .then(Mono.defer(() -> record(account, actorId)))
                            .thenReturn(new StaffAccountPassword(account, password));
                });
    }

    private static void refuseUnlessStaffWithSignIn(User account) {
        boolean staff = (AccountService.STAFF_ADMIN.equals(account.getCreatedVia())
                || AccountService.STAFF_SYNC.equals(account.getCreatedVia()))
                && account.getRoles() != null && account.getRoles().stream().anyMatch(UserType::isStaffRole);
        if (!staff) {
            throw new TranslatedRefusal(ErrorCode.NOT_A_STAFF_ACCOUNT, "only a platform staff account has a password to reset here");
        }
        AccountState state = AccountStates.of(account);
        boolean hasSignIn = account.getKeycloakUserId() != null && !account.getKeycloakUserId().isBlank();
        if (!hasSignIn || (state != AccountState.ACTIVE && state != AccountState.SUSPENDED)) {
            throw new TranslatedRefusal(ErrorCode.ACCOUNT_NOT_ACTIVE, "the account is " + state + " and has no password to reset");
        }
    }

    /** The row says who reset whom; a row that cannot be written is logged and never undoes the reset. */
    private Mono<Void> record(User account, String actorId) {
        return audit.record(AuditLog.AuditAction.STAFF_PASSWORD_RESET, "User", account.getId(), actorId,
                        Map.of("trigger", "SUPER_ADMIN_RESET"))
                .then()
                .onErrorResume(error -> {
                    log.error("A staff sign-in reset for account {} succeeded but its audit row could not be written: {}",
                            account.getId(), error.getClass().getSimpleName());
                    return Mono.empty();
                });
    }
}
