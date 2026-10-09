package com.pml.identity.migration;

import com.pml.identity.persistence.IdentityCollections;
import com.pml.shared.migration.DocumentBackfill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Brings identity-service's stored documents up to the shape the current models and validators require.
 *
 * <h2>What a live database is missing without this</h2>
 * Each entry below closes a gap that a code change opened and cannot close by itself:
 *
 * <ul>
 *   <li><b>{@code _class}</b> — {@code @TypeAlias} changed what new writes store; existing
 *       documents keep the fully-qualified class name and stop deserialising the day that class
 *       moves package.</li>
 *   <li><b>{@code version}</b> — a document with {@code @Version} and no {@code version} field
 *       is treated as new, so optimistic locking guards nothing until something writes it.</li>
 *   <li><b>{@code currency}</b> — {@code @Builder.Default} runs when an object is built, not when
 *       one is read, so amounts written earlier deserialise with a null currency.</li>
 * </ul>
 *
 * <p>Runs after the collection renames: these filters name registry collections, and before the
 * rename the documents are still under their old names where nothing here would match them.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class IdentityRegistryConformanceMigrationService {

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    public Mono<DocumentBackfill.Result> migrate() {
        return new DocumentBackfill(mongoTemplateProvider.getObject())
                // _class → the alias each document now declares
                .rewriteClassTo(IdentityCollections.AUDIT_LOGS,
                        "com.pml.identity.domain.model.AuditLog", "audit_logs")
                .rewriteClassTo(IdentityCollections.EVENT_ACCESS_GRANTS,
                        "com.pml.identity.domain.model.EventAccessGrant", "event_access_grants")
                .rewriteClassTo(IdentityCollections.EVENT_REMINDERS,
                        "com.pml.identity.domain.model.EventReminder", "event_reminders")
                .rewriteClassTo(IdentityCollections.NOTIFICATION_PREFERENCES,
                        "com.pml.identity.domain.model.NotificationPreferences", "notification_preferences")
                .rewriteClassTo(IdentityCollections.NOTIFICATIONS,
                        "com.pml.identity.domain.model.Notification", "notifications")
                .rewriteClassTo(IdentityCollections.ORGANIZATION_MEMBERS,
                        "com.pml.identity.domain.model.OrganizationMember", "organization_members")
                .rewriteClassTo(IdentityCollections.ORGANIZATIONS,
                        "com.pml.identity.domain.model.Organization", "organizations")
                .rewriteClassTo(IdentityCollections.OWNERSHIP_TRANSFERS,
                        "com.pml.identity.domain.model.OwnershipTransferRequest", "ownership_transfers")
                .rewriteClassTo(IdentityCollections.PAYOUT_CONFIG_AUDIT_LOGS,
                        "com.pml.identity.domain.model.PayoutConfigAuditLog", "payout_config_audit_logs")
                .rewriteClassTo(IdentityCollections.PERMISSIONS,
                        "com.pml.identity.domain.model.Permission", "permissions")
                .rewriteClassTo(IdentityCollections.ROLE_PERMISSIONS,
                        "com.pml.identity.domain.model.RolePermission", "role_permissions")
                .rewriteClassTo(IdentityCollections.TEAM_INVITATIONS,
                        "com.pml.identity.domain.model.TeamInvitation", "team_invitations")
                .rewriteClassTo(IdentityCollections.TOKEN_REVOCATIONS,
                        "com.pml.identity.security.revocation.RevocationRecord", "token_revocations")
                .rewriteClassTo(IdentityCollections.USER_DEVICES,
                        "com.pml.identity.domain.model.UserDevice", "user_devices")
                .rewriteClassTo(IdentityCollections.USERS,
                        "com.pml.identity.domain.model.User", "users")
                .rewriteClassTo(IdentityCollections.VERIFICATION_DOCUMENTS,
                        "com.pml.identity.domain.model.VerificationDocument", "verification_documents")
                .run();
    }
}
