package com.pml.identity.migration;

import com.pml.identity.persistence.IdentityCollections;
import com.pml.shared.migration.CollectionRenameMigration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Moves identity's collections onto their service-prefixed names.
 *
 * <h2>An unmigrated identity service does not look broken</h2>
 * Reading an empty {@code users} collection is not an error. Sign-in still works — Keycloak is
 * the identity provider — and the service simply finds no profile, so it behaves like a platform
 * where nobody has ever registered.
 *
 * <h2>{@code platform_configuration} is not identity's to move</h2>
 * The platform settings are catalog's table, and catalog's migration renames them. Identity only
 * reads them, through shared-library's {@code PlatformConfigurationReader}.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class IdentityCollectionRenameMigrationService {

    /** Unprefixed name → the service-prefixed name it moves to. */
    private static final Map<String, String> RENAMES = CollectionRenameMigration.table(
            "users", IdentityCollections.USERS,
            "organizations", IdentityCollections.ORGANIZATIONS,
            "organization_members", IdentityCollections.ORGANIZATION_MEMBERS,
            "team_invitations", IdentityCollections.TEAM_INVITATIONS,
            "ownership_transfers", IdentityCollections.OWNERSHIP_TRANSFERS,
            "event_access_grants", IdentityCollections.EVENT_ACCESS_GRANTS,
            "permissions", IdentityCollections.PERMISSIONS,
            "role_permissions", IdentityCollections.ROLE_PERMISSIONS,
            "verification_documents", IdentityCollections.VERIFICATION_DOCUMENTS,
            "notifications", IdentityCollections.NOTIFICATIONS,
            "notification_preferences", IdentityCollections.NOTIFICATION_PREFERENCES,
            "user_devices", IdentityCollections.USER_DEVICES,
            "event_reminders", IdentityCollections.EVENT_REMINDERS,
            "audit_logs", IdentityCollections.AUDIT_LOGS,
            "token_revocations", IdentityCollections.TOKEN_REVOCATIONS,
            "payout_config_audit_logs", IdentityCollections.PAYOUT_CONFIG_AUDIT_LOGS);

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    public Mono<CollectionRenameMigration.Result> migrate() {
        return new CollectionRenameMigration(mongoTemplateProvider.getObject()).migrate(RENAMES);
    }
}
