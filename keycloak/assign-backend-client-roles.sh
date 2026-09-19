#!/bin/bash
set -e

# Script to assign realm-management roles to silingan-backend service account
# This allows the backend to manage users, clients, and realms via Keycloak Admin API

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="${REALM:-silingan-platform}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-admin}"

echo "=========================================="
echo "Assigning realm-management roles to silingan-backend service account"
echo "Keycloak URL: $KEYCLOAK_URL"
echo "Realm: $REALM"
echo "=========================================="

# Get admin token
echo "1. Authenticating as admin..."
ADMIN_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli" \
  -d "username=$ADMIN_USER" \
  -d "password=$ADMIN_PASS" \
  -d "grant_type=password" | jq -r '.access_token')

if [ "$ADMIN_TOKEN" = "null" ] || [ -z "$ADMIN_TOKEN" ]; then
  echo "❌ Failed to authenticate as admin"
  exit 1
fi
echo "   ✓ Got admin token"

# Get silingan-backend client ID
echo "2. Finding silingan-backend client..."
CLIENT_ID=$(curl -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=silingan-backend" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id')

if [ -z "$CLIENT_ID" ] || [ "$CLIENT_ID" = "null" ]; then
  echo "❌ Could not find silingan-backend client"
  exit 1
fi
echo "   ✓ Client ID: $CLIENT_ID"

# Get realm-management client ID
echo "3. Finding realm-management client..."
REALM_MGMT_ID=$(curl -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=realm-management" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id')

if [ -z "$REALM_MGMT_ID" ] || [ "$REALM_MGMT_ID" = "null" ]; then
  echo "❌ Could not find realm-management client"
  exit 1
fi
echo "   ✓ Realm Management Client ID: $REALM_MGMT_ID"

# Get service account user for silingan-backend
echo "4. Getting service account user..."
SERVICE_ACCOUNT_ID=$(curl -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM/clients/$CLIENT_ID/service-account-user" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.id')

if [ -z "$SERVICE_ACCOUNT_ID" ] || [ "$SERVICE_ACCOUNT_ID" = "null" ]; then
  echo "❌ Could not find service account user"
  exit 1
fi
echo "   ✓ Service Account ID: $SERVICE_ACCOUNT_ID"

# Get the specific roles and build the assignment payload
echo "5. Fetching available roles from realm-management..."
ROLES_TO_ASSIGN=$(curl -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM/clients/$REALM_MGMT_ID/roles" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -c '[.[] | select(.name | test("manage-users|manage-clients|manage-realm|manage-users-groups|query-users|query-clients|query-realms|view-clients|view-realm"))]')

if [ -z "$ROLES_TO_ASSIGN" ] || [ "$ROLES_TO_ASSIGN" = "[]" ]; then
  echo "❌ Could not find roles to assign"
  exit 1
fi

echo "   ✓ Found roles to assign:"
echo "$ROLES_TO_ASSIGN" | jq '.[].name' | sed 's/^/     - /'

# Assign roles to service account
echo "6. Assigning roles to service account..."
HTTP_STATUS=$(curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM/users/$SERVICE_ACCOUNT_ID/role-mappings/clients/$REALM_MGMT_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$ROLES_TO_ASSIGN" \
  -w "%{http_code}" \
  -o /dev/null)

if [ "$HTTP_STATUS" != "204" ]; then
  echo "❌ Failed to assign roles (HTTP $HTTP_STATUS)"
  exit 1
fi

echo "   ✓ Roles assigned successfully!"

echo ""
echo "=========================================="
echo "✅ DONE!"
echo "=========================================="
echo ""
echo "The silingan-backend client can now:"
echo "  • manage-users (create, update, delete users)"
echo "  • query-users (search/query users)"
echo "  • manage-clients (manage client configurations)"
echo "  • query-clients (query client info)"
echo "  • manage-realm (manage realm settings)"
echo "  • query-realms (query realm info)"
echo "  • view-clients (view client details)"
echo "  • view-realm (view realm details)"
echo ""
echo "Use this client to call Keycloak Admin API:"
echo "  client_id: silingan-backend"
echo "  client_secret: YVvWkbSHaxBM5o3l3Mqwr2jFYtBRmHK0"
echo "  grant_type: client_credentials"
