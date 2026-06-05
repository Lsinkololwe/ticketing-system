#!/bin/bash
# =============================================================================
# Fix Registration Flow Execution Order
# =============================================================================
# This script fixes the order of executions in the registration flow to ensure
# AccountTypeRoleMapper runs AFTER Registration User Creation.
#
# The correct order is:
# 1. Registration User Creation (creates the user)
# 2. Profile Validation (validates profile data)
# 3. Password Validation (validates password)
# 4. Account Type Role Mapper (assigns roles - MUST be last)
# =============================================================================

set -e

# Configuration
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8084}"
REALM="${KEYCLOAK_REALM:-event-ticketing}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin_password}"

echo "=============================================="
echo "Fix Registration Flow Execution Order"
echo "=============================================="
echo "Keycloak URL: $KEYCLOAK_URL"
echo "Realm: $REALM"
echo ""

# Get admin token
echo "Getting admin token..."
TOKEN_RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$ADMIN_USER" \
  -d "password=$ADMIN_PASSWORD" \
  -d "grant_type=password" \
  -d "client_id=admin-cli")

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$ACCESS_TOKEN" ]; then
  echo "ERROR: Failed to get admin token"
  echo "Response: $TOKEN_RESPONSE"
  exit 1
fi

echo "Got admin token."

# Find the registration flow
echo ""
echo "Finding registration flow..."

FLOWS=$(curl -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM/authentication/flows" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json")

# Look for our custom registration flow or the default one
FLOW_ALIAS=$(echo "$FLOWS" | grep -o '"alias":"[^"]*registration[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$FLOW_ALIAS" ]; then
  echo "ERROR: No registration flow found"
  exit 1
fi

echo "Found flow: $FLOW_ALIAS"

# Get the flow ID
FLOW_ID=$(echo "$FLOWS" | grep -B5 "\"alias\":\"$FLOW_ALIAS\"" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

echo "Flow ID: $FLOW_ID"

# Get all executions in the flow
echo ""
echo "Getting executions..."

EXECUTIONS=$(curl -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM/authentication/flows/$FLOW_ALIAS/executions" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json")

echo "$EXECUTIONS" | grep -E '"displayName"|"providerId"' | head -20

# Find the Account Type Role Mapper execution
echo ""
echo "Looking for Account Type Role Mapper..."

ACCOUNT_TYPE_EXECUTION_ID=$(echo "$EXECUTIONS" | grep -B5 '"providerId":"account-type-role-mapper"' | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$ACCOUNT_TYPE_EXECUTION_ID" ]; then
  echo "Account Type Role Mapper not found in flow."
  echo "Please add it first via Admin Console or setup script."
  exit 1
fi

echo "Found Account Type Role Mapper: $ACCOUNT_TYPE_EXECUTION_ID"

# Move it to the end (lower priority = runs later)
echo ""
echo "Moving Account Type Role Mapper to run last..."

# Lower the execution multiple times to ensure it's at the end
for i in {1..5}; do
  curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM/authentication/executions/$ACCOUNT_TYPE_EXECUTION_ID/lower-priority" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json"
  echo "  Moved down ($i/5)"
done

# Verify the new order
echo ""
echo "Verifying new execution order..."

EXECUTIONS_AFTER=$(curl -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM/authentication/flows/$FLOW_ALIAS/executions" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json")

echo ""
echo "Current execution order:"
echo "$EXECUTIONS_AFTER" | grep -E '"displayName"|"providerId"' | grep -A1 '"displayName"' | head -30

echo ""
echo "=============================================="
echo "Done! Account Type Role Mapper should now be"
echo "at the END of the registration form sub-flow."
echo ""
echo "The correct order is:"
echo "  1. Registration User Creation"
echo "  2. Profile Validation"
echo "  3. Password Validation"
echo "  4. Account Type Role Mapper (LAST)"
echo "=============================================="
