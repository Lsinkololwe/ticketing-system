package com.pml.keycloak.registration;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

/**
 * Factory for creating AccountTypeRoleMapper instances.
 *
 * <h2>Registration</h2>
 * <p>
 * This factory is registered via SPI in:
 * META-INF/services/org.keycloak.authentication.FormActionFactory
 * </p>
 *
 * <h2>Keycloak Admin Console Setup</h2>
 * <ol>
 *   <li>Go to Authentication → Flows</li>
 *   <li>Select "Registration" flow (or copy it first)</li>
 *   <li>Click "Add execution" on "Registration form"</li>
 *   <li>Select "Account Type Role Mapper"</li>
 *   <li>Set requirement to REQUIRED</li>
 *   <li>Move it AFTER "Profile Validation" but BEFORE any email verification</li>
 * </ol>
 *
 * @see AccountTypeRoleMapper
 */
public class AccountTypeRoleMapperFactory implements FormActionFactory {

    private static final Logger LOG = Logger.getLogger(AccountTypeRoleMapperFactory.class);

    /**
     * Provider ID used to identify this FormAction in Keycloak.
     */
    public static final String PROVIDER_ID = "account-type-role-mapper";

    /**
     * Display name shown in Keycloak Admin Console.
     */
    private static final String DISPLAY_NAME = "Account Type Role Mapper";

    /**
     * Help text shown in Keycloak Admin Console.
     */
    private static final String HELP_TEXT = "Maps user-selected account types (CUSTOMER, ORGANIZER) " +
            "from registration form to Keycloak realm roles. Ensures users get proper roles " +
            "based on their registration choices.";

    @Override
    public FormAction create(KeycloakSession session) {
        return new AccountTypeRoleMapper();
    }

    @Override
    public void init(Config.Scope config) {
        LOG.info("AccountTypeRoleMapperFactory initialized");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        LOG.debug("AccountTypeRoleMapperFactory postInit completed");
    }

    @Override
    public void close() {
        LOG.debug("AccountTypeRoleMapperFactory closed");
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return DISPLAY_NAME;
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        // No configuration options for now
        // Could add: default roles, allowed roles, auto-create roles toggle
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[] {
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return HELP_TEXT;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        // No configuration properties for now
        // Future: Could add configurable properties like:
        // - default_role: Default role to assign
        // - require_at_least_one: Whether to require at least one account type
        // - auto_create_roles: Whether to auto-create missing roles
        return List.of();
    }
}
