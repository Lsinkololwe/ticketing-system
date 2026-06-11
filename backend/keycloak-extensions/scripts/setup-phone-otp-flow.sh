#!/bin/bash

# Setup Phone OTP Authentication Flow in Keycloak
# Run this script after deploying the phone-otp-authenticator JAR

set -e

# Configuration
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8084}"
REALM="${KEYCLOAK_REALM:-myticketzm}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"

echo "=== Setting up Phone OTP Authentication Flow ==="
echo "Keycloak URL: $KEYCLOAK_URL"
echo "Realm: $REALM"

# Get admin access token
echo "Getting admin access token..."
TOKEN_RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$ADMIN_USER" \
  -d "password=$ADMIN_PASSWORD" \
  -d "grant_type=password" \
  -d "client_id=admin-cli")

ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ACCESS_TOKEN" ]; then
  echo "Error: Failed to get access token"
  echo "Response: $TOKEN_RESPONSE"
  exit 1
fi

echo "Access token obtained successfully"

# Function to make API calls
api_call() {
  local method=$1
  local endpoint=$2
  local data=$3

  if [ -n "$data" ]; then
    curl -s -X $method "$KEYCLOAK_URL/admin/realms/$REALM$endpoint" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -H "Content-Type: application/json" \
      -d "$data"
  else
    curl -s -X $method "$KEYCLOAK_URL/admin/realms/$REALM$endpoint" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -H "Content-Type: application/json"
  fi
}

# Check if phone-otp-authenticator is available
echo "Checking if phone-otp-authenticator is available..."
AUTHENTICATORS=$(api_call GET "/authentication/authenticator-providers")

if echo "$AUTHENTICATORS" | grep -q "phone-otp-authenticator"; then
  echo "Phone OTP Authenticator is available"
else
  echo "Error: Phone OTP Authenticator not found. Make sure the JAR is deployed and Keycloak is restarted."
  echo "Available authenticators:"
  echo "$AUTHENTICATORS" | grep -o '"id":"[^"]*' | cut -d'"' -f4
  exit 1
fi

# Create the phone-otp-browser flow
echo "Creating phone-otp-browser authentication flow..."

# Check if flow already exists
EXISTING_FLOW=$(api_call GET "/authentication/flows" | grep -o '"alias":"phone-otp-browser"' || true)

if [ -n "$EXISTING_FLOW" ]; then
  echo "Flow 'phone-otp-browser' already exists. Skipping creation."
else
  # Create main flow
  api_call POST "/authentication/flows" '{
    "alias": "phone-otp-browser",
    "description": "Browser flow with phone OTP authentication option",
    "providerId": "basic-flow",
    "topLevel": true,
    "builtIn": false
  }'
  echo "Created phone-otp-browser flow"
fi

# Get flow ID
FLOW_ID=$(api_call GET "/authentication/flows" | grep -B5 '"alias":"phone-otp-browser"' | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

if [ -z "$FLOW_ID" ]; then
  echo "Error: Could not get flow ID"
  exit 1
fi

echo "Flow ID: $FLOW_ID"

# Add Cookie execution (ALTERNATIVE)
echo "Adding Cookie execution..."
api_call POST "/authentication/flows/phone-otp-browser/executions/execution" '{
  "provider": "auth-cookie"
}'

# Add Identity Provider Redirector (ALTERNATIVE)
echo "Adding Identity Provider Redirector..."
api_call POST "/authentication/flows/phone-otp-browser/executions/execution" '{
  "provider": "identity-provider-redirector"
}'

# Create Forms sub-flow
echo "Creating Forms sub-flow..."
api_call POST "/authentication/flows/phone-otp-browser/executions/flow" '{
  "alias": "phone-otp-forms",
  "description": "Username/password or phone OTP",
  "provider": "registration-page-form",
  "type": "basic-flow"
}'

# Get executions to update requirements
echo "Updating execution requirements..."
EXECUTIONS=$(api_call GET "/authentication/flows/phone-otp-browser/executions")

# Parse and update each execution
echo "$EXECUTIONS" | python3 -c "
import json
import sys
data = json.load(sys.stdin)
for exec in data:
    print(f\"{exec.get('id')}:{exec.get('displayName')}:{exec.get('requirement')}\")
" | while IFS=: read -r id name req; do
  if [ "$name" = "Cookie" ] || [ "$name" = "Identity Provider Redirector" ]; then
    api_call PUT "/authentication/flows/phone-otp-browser/executions" "{
      \"id\": \"$id\",
      \"requirement\": \"ALTERNATIVE\"
    }"
    echo "Set $name to ALTERNATIVE"
  fi
done

# Get Forms sub-flow ID
FORMS_FLOW_ID=$(api_call GET "/authentication/flows" | grep -B5 '"alias":"phone-otp-forms"' | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

if [ -n "$FORMS_FLOW_ID" ]; then
  echo "Forms sub-flow ID: $FORMS_FLOW_ID"

  # Add Username Password Form to sub-flow
  echo "Adding Username Password Form..."
  api_call POST "/authentication/flows/phone-otp-forms/executions/execution" '{
    "provider": "auth-username-password-form"
  }'

  # Add Phone OTP Authenticator to sub-flow
  echo "Adding Phone OTP Authenticator..."
  api_call POST "/authentication/flows/phone-otp-forms/executions/execution" '{
    "provider": "phone-otp-authenticator"
  }'

  # Update sub-flow executions to ALTERNATIVE
  FORMS_EXECUTIONS=$(api_call GET "/authentication/flows/phone-otp-forms/executions")
  echo "$FORMS_EXECUTIONS" | python3 -c "
import json
import sys
data = json.load(sys.stdin)
for exec in data:
    print(f\"{exec.get('id')}:{exec.get('displayName')}\")
" | while IFS=: read -r id name; do
    api_call PUT "/authentication/flows/phone-otp-forms/executions" "{
      \"id\": \"$id\",
      \"requirement\": \"ALTERNATIVE\"
    }"
    echo "Set $name to ALTERNATIVE"
  done
fi

echo ""
echo "=== Phone OTP Authentication Flow Setup Complete ==="
echo ""
echo "Next steps:"
echo "1. Go to Keycloak Admin Console > Authentication > Bindings"
echo "2. Set 'Browser Flow' to 'phone-otp-browser'"
echo "3. Test the flow by logging in"
echo ""
echo "Or run this command to bind the flow:"
echo "curl -X PUT '$KEYCLOAK_URL/admin/realms/$REALM' \\"
echo "  -H 'Authorization: Bearer <TOKEN>' \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"browserFlow\": \"phone-otp-browser\"}'"
