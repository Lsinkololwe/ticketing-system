#!/bin/bash

# ============================================================================
# Setup Registration Flow with Account Type Role Mapper
# ============================================================================
#
# This script configures Keycloak's registration flow to include the
# AccountTypeRoleMapper form action, which assigns realm roles based on
# the user's account type selection during registration.
#
# Prerequisites:
# - Keycloak running and accessible
# - keycloak-extensions.jar deployed to providers/
# - Admin credentials available
#
# Usage:
#   ./setup-registration-flow.sh
#
# Environment Variables:
#   KEYCLOAK_URL     - Keycloak base URL (default: http://localhost:8084)
#   KEYCLOAK_REALM   - Target realm (default: event-ticketing)
#   KEYCLOAK_ADMIN   - Admin username (default: admin)
#   KEYCLOAK_ADMIN_PASSWORD - Admin password (required)
#
# ============================================================================

set -e

# Configuration
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8084}"
REALM="${KEYCLOAK_REALM:-myticketzm}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin}"

echo "========================================"
echo "Keycloak Registration Flow Setup"
echo "========================================"
echo "URL: $KEYCLOAK_URL"
echo "Realm: $REALM"
echo "Admin: $ADMIN_USER"
echo "========================================"

# Get admin token
echo "Obtaining admin token..."
TOKEN_RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "username=$ADMIN_USER" \
    -d "password=$ADMIN_PASS" \
    -d "grant_type=password" \
    -d "client_id=admin-cli")

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$ACCESS_TOKEN" ]; then
    echo "ERROR: Failed to obtain admin token"
    echo "Response: $TOKEN_RESPONSE"
    exit 1
fi

echo "Token obtained successfully"

# Function to make authenticated API calls
kc_api() {
    local METHOD=$1
    local ENDPOINT=$2
    local DATA=$3

    if [ -n "$DATA" ]; then
        curl -s -X "$METHOD" "$KEYCLOAK_URL/admin/realms/$REALM$ENDPOINT" \
            -H "Authorization: Bearer $ACCESS_TOKEN" \
            -H "Content-Type: application/json" \
            -d "$DATA"
    else
        curl -s -X "$METHOD" "$KEYCLOAK_URL/admin/realms/$REALM$ENDPOINT" \
            -H "Authorization: Bearer $ACCESS_TOKEN" \
            -H "Content-Type: application/json"
    fi
}

# ============================================================================
# Step 1: Ensure required realm roles exist
# ============================================================================
echo ""
echo "Step 1: Creating realm roles..."

create_role() {
    local ROLE_NAME=$1
    local ROLE_DESC=$2

    # Check if role exists
    EXISTING=$(kc_api GET "/roles/$ROLE_NAME" 2>/dev/null || echo "")

    if echo "$EXISTING" | grep -q "\"name\":\"$ROLE_NAME\""; then
        echo "  Role '$ROLE_NAME' already exists"
    else
        echo "  Creating role '$ROLE_NAME'..."
        kc_api POST "/roles" "{\"name\":\"$ROLE_NAME\",\"description\":\"$ROLE_DESC\"}"
        echo "  Role '$ROLE_NAME' created"
    fi
}

create_role "CUSTOMER" "Customer role - can browse and purchase tickets"
create_role "ORGANIZER" "Organizer role - can create events and sell tickets"
create_role "ADMIN" "Administrator role - platform management"
create_role "SCANNER" "Scanner role - can validate tickets at venues"
create_role "FINANCE" "Finance role - can manage payouts and financials"

# ============================================================================
# Step 2: Get the registration flow
# ============================================================================
echo ""
echo "Step 2: Checking registration flow..."

# Get all authentication flows
FLOWS=$(kc_api GET "/authentication/flows")

# Find the registration flow
REG_FLOW_ID=$(echo "$FLOWS" | grep -o '"id":"[^"]*","alias":"registration"' | head -1 | cut -d'"' -f4)

if [ -z "$REG_FLOW_ID" ]; then
    echo "ERROR: Registration flow not found"
    exit 1
fi

echo "  Registration flow ID: $REG_FLOW_ID"

# ============================================================================
# Step 3: Get registration form executions
# ============================================================================
echo ""
echo "Step 3: Getting registration form executions..."

EXECUTIONS=$(kc_api GET "/authentication/flows/registration/executions")
echo "  Found executions"

# Find the "registration form" sub-flow
REG_FORM_ID=$(echo "$EXECUTIONS" | grep -o '"id":"[^"]*"[^}]*"displayName":"registration form"' | head -1 | cut -d'"' -f4)

if [ -z "$REG_FORM_ID" ]; then
    echo "WARNING: Registration form sub-flow not found, looking for alternatives..."
    # Try to find by providerId
    REG_FORM_ID=$(echo "$EXECUTIONS" | grep -o '"id":"[^"]*"[^}]*"providerId":"registration-page-form"' | head -1 | cut -d'"' -f4)
fi

if [ -z "$REG_FORM_ID" ]; then
    echo "ERROR: Cannot find registration form execution"
    exit 1
fi

echo "  Registration form ID: $REG_FORM_ID"

# ============================================================================
# Step 4: Check if AccountTypeRoleMapper is already added
# ============================================================================
echo ""
echo "Step 4: Checking if AccountTypeRoleMapper is already configured..."

EXISTING_MAPPER=$(echo "$EXECUTIONS" | grep -o '"providerId":"account-type-role-mapper"' || echo "")

if [ -n "$EXISTING_MAPPER" ]; then
    echo "  AccountTypeRoleMapper is already configured!"
    echo ""
    echo "Setup complete - no changes needed."
    exit 0
fi

echo "  AccountTypeRoleMapper not found, will add it..."

# ============================================================================
# Step 5: Add AccountTypeRoleMapper execution
# ============================================================================
echo ""
echo "Step 5: Adding AccountTypeRoleMapper to registration flow..."

# Get the alias of the registration form flow
REG_FORM_ALIAS=$(echo "$EXECUTIONS" | grep -o '"flowId":"[^"]*"[^}]*"displayName":"registration form"' | head -1)
# This is tricky - we need to add the execution to the registration-form sub-flow

# Try to add execution to the registration form
ADD_RESULT=$(kc_api POST "/authentication/flows/registration form/executions/execution" \
    '{"provider":"account-type-role-mapper"}')

if echo "$ADD_RESULT" | grep -q "error"; then
    echo "WARNING: Could not add via 'registration form', trying alternate method..."

    # Try using the flow ID directly
    ADD_RESULT=$(kc_api POST "/authentication/flows/registration/executions/execution" \
        '{"provider":"account-type-role-mapper"}')
fi

echo "  Result: $ADD_RESULT"

# ============================================================================
# Step 6: Set the execution to REQUIRED
# ============================================================================
echo ""
echo "Step 6: Configuring execution requirement..."

# Re-fetch executions to get the new execution ID
EXECUTIONS=$(kc_api GET "/authentication/flows/registration/executions")

MAPPER_EXEC=$(echo "$EXECUTIONS" | grep -o '"id":"[^"]*"[^}]*"providerId":"account-type-role-mapper"')
MAPPER_ID=$(echo "$MAPPER_EXEC" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -n "$MAPPER_ID" ]; then
    echo "  Setting AccountTypeRoleMapper to REQUIRED..."
    kc_api PUT "/authentication/flows/registration/executions" \
        "{\"id\":\"$MAPPER_ID\",\"requirement\":\"REQUIRED\"}"
    echo "  Done"
else
    echo "WARNING: Could not find AccountTypeRoleMapper execution ID"
fi

# ============================================================================
# Step 7: Verify setup
# ============================================================================
echo ""
echo "Step 7: Verifying setup..."

FINAL_EXECUTIONS=$(kc_api GET "/authentication/flows/registration/executions")
VERIFIED=$(echo "$FINAL_EXECUTIONS" | grep -o '"providerId":"account-type-role-mapper"' || echo "")

if [ -n "$VERIFIED" ]; then
    echo "  AccountTypeRoleMapper is configured!"
else
    echo "WARNING: AccountTypeRoleMapper may not be properly configured"
    echo "  Please verify manually in Keycloak Admin Console:"
    echo "  1. Go to Authentication > Flows"
    echo "  2. Select 'registration' flow"
    echo "  3. Add execution 'Account Type Role Mapper' to 'registration form'"
    echo "  4. Set requirement to REQUIRED"
fi

echo ""
echo "========================================"
echo "Setup Complete!"
echo "========================================"
echo ""
echo "Next steps:"
echo "1. Verify the setup in Keycloak Admin Console"
echo "2. Test registration with CUSTOMER and ORGANIZER selections"
echo "3. Verify users receive correct realm roles"
echo ""
