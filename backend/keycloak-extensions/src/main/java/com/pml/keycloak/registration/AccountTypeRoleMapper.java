package com.pml.keycloak.registration;

import org.jboss.logging.Logger;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.validation.Validation;

import jakarta.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.List;

/**
 * Keycloak Registration Form Action that maps accountType attribute to realm roles.
 *
 * <h2>Purpose</h2>
 * <p>
 * During registration, users select their account type(s) via checkboxes:
 * - CUSTOMER: Can browse and purchase tickets
 * - ORGANIZER: Can create events and sell tickets
 * </p>
 *
 * <p>
 * This FormAction:
 * 1. Validates that at least one account type is selected
 * 2. Assigns corresponding Keycloak realm roles (CUSTOMER, ORGANIZER)
 * 3. Stores the selection in user attributes for sync to MongoDB
 * </p>
 *
 * <h2>Registration Form Integration</h2>
 * <p>
 * The registration form submits: {@code user.attributes.accountType} with values
 * ["CUSTOMER"], ["ORGANIZER"], or ["CUSTOMER", "ORGANIZER"]
 * </p>
 *
 * <h2>Configuration</h2>
 * <p>
 * Add this FormAction to the registration flow in Keycloak:
 * Authentication → Flows → Registration → Add execution → account-type-role-mapper
 * </p>
 *
 * @see AccountTypeRoleMapperFactory
 */
public class AccountTypeRoleMapper implements FormAction {

    private static final Logger LOG = Logger.getLogger(AccountTypeRoleMapper.class);

    /**
     * Form field name for account type selection.
     * Matches the input name in register.ftl: user.attributes.accountType
     */
    public static final String FIELD_ACCOUNT_TYPE = "user.attributes.accountType";

    /**
     * Keycloak attribute name where account types are stored.
     */
    public static final String ATTR_ACCOUNT_TYPE = "accountType";

    /**
     * Supported account types that map to realm roles.
     */
    public static final String TYPE_CUSTOMER = "CUSTOMER";
    public static final String TYPE_ORGANIZER = "ORGANIZER";

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {
        // No additional page building needed
        // The form fields are defined in register.ftl
    }

    @Override
    public void validate(ValidationContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        List<FormMessage> errors = new ArrayList<>();

        // Get selected account types
        List<String> accountTypes = formData.get(FIELD_ACCOUNT_TYPE);

        // Validate at least one account type is selected
        if (accountTypes == null || accountTypes.isEmpty()) {
            errors.add(new FormMessage(FIELD_ACCOUNT_TYPE, "accountTypeRequired"));
            LOG.warn("Registration validation failed: No account type selected");
        } else {
            // Validate that selected types are valid
            for (String type : accountTypes) {
                if (!TYPE_CUSTOMER.equals(type) && !TYPE_ORGANIZER.equals(type)) {
                    errors.add(new FormMessage(FIELD_ACCOUNT_TYPE, "invalidAccountType"));
                    LOG.warnf("Registration validation failed: Invalid account type '%s'", type);
                    break;
                }
            }
        }

        if (!errors.isEmpty()) {
            context.error(Errors.INVALID_REGISTRATION);
            context.validationError(formData, errors);
            return;
        }

        context.success();
    }

    @Override
    public void success(FormContext context) {
        UserModel user = context.getUser();

        // Defensive check: user must be created by Registration User Creation first
        if (user == null) {
            LOG.warn("AccountTypeRoleMapper.success() called but user is null. " +
                    "Ensure this action is placed AFTER 'Registration User Creation' in the flow.");
            return;
        }

        RealmModel realm = context.getRealm();
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        List<String> accountTypes = formData.get(FIELD_ACCOUNT_TYPE);

        if (accountTypes == null || accountTypes.isEmpty()) {
            // Default to CUSTOMER if somehow no selection made (shouldn't happen after validation)
            accountTypes = List.of(TYPE_CUSTOMER);
        }

        LOG.infof("Processing account types for user %s: %s", user.getUsername(), accountTypes);

        // Store account types as user attribute (for MongoDB sync)
        user.setAttribute(ATTR_ACCOUNT_TYPE, accountTypes);

        // Also store as 'roles' attribute for backward compatibility with sync service
        user.setAttribute("roles", accountTypes);

        // Assign realm roles based on account type selection
        for (String accountType : accountTypes) {
            assignRealmRole(realm, user, accountType);
        }

        // Always ensure CUSTOMER role is assigned (base role for all users)
        if (!accountTypes.contains(TYPE_CUSTOMER)) {
            assignRealmRole(realm, user, TYPE_CUSTOMER);
            // Also add to attributes
            List<String> updatedTypes = new ArrayList<>(accountTypes);
            updatedTypes.add(TYPE_CUSTOMER);
            user.setAttribute(ATTR_ACCOUNT_TYPE, updatedTypes);
            user.setAttribute("roles", updatedTypes);
        }

        LOG.infof("Successfully assigned roles to user %s: %s", user.getUsername(), accountTypes);
    }

    /**
     * Assigns a realm role to the user.
     *
     * @param realm The Keycloak realm
     * @param user The user to assign the role to
     * @param roleName The role name to assign
     */
    private void assignRealmRole(RealmModel realm, UserModel user, String roleName) {
        RoleModel role = realm.getRole(roleName);

        if (role == null) {
            LOG.warnf("Realm role '%s' not found in realm '%s'. Creating it now.", roleName, realm.getName());
            // Create the role if it doesn't exist
            role = realm.addRole(roleName);
            role.setDescription("Auto-created role for " + roleName + " users");
        }

        if (!user.hasRole(role)) {
            user.grantRole(role);
            LOG.infof("Assigned role '%s' to user '%s'", roleName, user.getUsername());
        } else {
            LOG.debugf("User '%s' already has role '%s'", user.getUsername(), roleName);
        }
    }

    @Override
    public boolean requiresUser() {
        // IMPORTANT: Must return false for registration flows!
        // Returning true causes Keycloak to check for user existence BEFORE any actions run,
        // which fails during registration since the user doesn't exist yet.
        // The actual user availability is handled by placing this action AFTER
        // 'Registration User Creation' in the flow, with a defensive null check in success().
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        // Always configured
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions needed
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
