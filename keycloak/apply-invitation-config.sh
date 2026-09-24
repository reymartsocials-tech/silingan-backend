#!/bin/bash
set -euo pipefail

# Applies the Silingan invitation configuration to an ALREADY EXISTING realm.
#
# `start-dev --import-realm` only imports a realm when it does not exist yet, so
# editing keycloak/import/silingan-platform-realm.json has no effect on a realm
# that was imported previously. Run this script to push the same configuration
# through the Admin REST API instead.
#
# It configures:
#   1. The declarative user profile, so the custom invitation attributes
#      (roleCode, communityId, ...) are actually persisted. Keycloak 26 drops
#      unknown attributes by default, which silently breaks the email theme.
#   2. The email theme (my-community-theme) and the SMTP server.
#   3. The admin-generated action token lifespan used by executeActionsEmail().
#
# Usage:
#   ./keycloak/apply-invitation-config.sh
#   KEYCLOAK_URL=... REALM=... ADMIN_USER=... ADMIN_PASS=... ./keycloak/apply-invitation-config.sh

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="${REALM:-silingan-platform}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-admin}"
EMAIL_THEME="${EMAIL_THEME:-my-community-theme}"
SMTP_HOST="${SMTP_HOST:-mailpit}"
SMTP_PORT="${SMTP_PORT:-1025}"
SMTP_FROM="${SMTP_FROM:-no-reply@silingan.local}"
SMTP_FROM_DISPLAY_NAME="${SMTP_FROM_DISPLAY_NAME:-Silingan}"
# Real providers (Gmail, SES, SendGrid, ...) need authentication and TLS.
# Example for Gmail (requires a Google App Password, not the account password):
#   SMTP_HOST=smtp.gmail.com SMTP_PORT=587 SMTP_STARTTLS=true SMTP_AUTH=true \
#   SMTP_USER=you@gmail.com SMTP_PASSWORD='xxxx xxxx xxxx xxxx' \
#   SMTP_FROM=you@gmail.com ./keycloak/apply-invitation-config.sh
SMTP_AUTH="${SMTP_AUTH:-false}"
SMTP_USER="${SMTP_USER:-}"
SMTP_PASSWORD="${SMTP_PASSWORD:-}"
SMTP_STARTTLS="${SMTP_STARTTLS:-false}"
SMTP_SSL="${SMTP_SSL:-false}"
SMTP_REPLY_TO="${SMTP_REPLY_TO:-}"
ACTION_TOKEN_LIFESPAN="${ACTION_TOKEN_LIFESPAN:-86400}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
USER_PROFILE_FILE="${USER_PROFILE_FILE:-$SCRIPT_DIR/config/silingan-user-profile.json}"

command -v jq >/dev/null 2>&1 || { echo "jq is required"; exit 1; }
[ -f "$USER_PROFILE_FILE" ] || { echo "Missing $USER_PROFILE_FILE"; exit 1; }

echo "=========================================="
echo "Applying Silingan invitation config"
echo "Keycloak: $KEYCLOAK_URL   Realm: $REALM"
echo "=========================================="

echo "1. Authenticating as admin..."
ADMIN_TOKEN=$(curl -sS -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli" \
  -d "username=$ADMIN_USER" \
  -d "password=$ADMIN_PASS" \
  -d "grant_type=password" | jq -r '.access_token')

if [ "$ADMIN_TOKEN" = "null" ] || [ -z "$ADMIN_TOKEN" ]; then
  echo "Failed to authenticate as admin"
  exit 1
fi

echo "2. Updating user profile (custom invitation attributes)..."
HTTP_CODE=$(curl -sS -o /tmp/silingan-up.out -w '%{http_code}' \
  -X PUT "$KEYCLOAK_URL/admin/realms/$REALM/users/profile" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary "@$USER_PROFILE_FILE")
if [ "$HTTP_CODE" -ge 300 ]; then
  echo "Failed to update user profile (HTTP $HTTP_CODE):"
  cat /tmp/silingan-up.out
  exit 1
fi
echo "   attributes: $(jq -r '[.attributes[].name] | join(", ")' "$USER_PROFILE_FILE")"

echo "3. Updating realm email theme, SMTP and action token lifespan..."
REALM_PATCH=$(jq -n \
  --arg theme "$EMAIL_THEME" \
  --arg host "$SMTP_HOST" \
  --arg port "$SMTP_PORT" \
  --arg from "$SMTP_FROM" \
  --arg fromName "$SMTP_FROM_DISPLAY_NAME" \
  --arg replyTo "$SMTP_REPLY_TO" \
  --arg auth "$SMTP_AUTH" \
  --arg user "$SMTP_USER" \
  --arg password "$SMTP_PASSWORD" \
  --arg starttls "$SMTP_STARTTLS" \
  --arg ssl "$SMTP_SSL" \
  --argjson lifespan "$ACTION_TOKEN_LIFESPAN" \
  '{
     emailTheme: $theme,
     actionTokenGeneratedByAdminLifespan: $lifespan,
     smtpServer: {
       host: $host, port: $port, from: $from, fromDisplayName: $fromName,
       replyTo: $replyTo, ssl: $ssl, starttls: $starttls,
       auth: $auth, user: $user, password: $password
     }
   }')

HTTP_CODE=$(curl -sS -o /tmp/silingan-realm.out -w '%{http_code}' \
  -X PUT "$KEYCLOAK_URL/admin/realms/$REALM" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$REALM_PATCH")
if [ "$HTTP_CODE" -ge 300 ]; then
  echo "Failed to update realm (HTTP $HTTP_CODE):"
  cat /tmp/silingan-realm.out
  exit 1
fi

echo "=========================================="
echo "Done. Verify with:"
echo "  curl -s -H \"Authorization: Bearer \$TOKEN\" $KEYCLOAK_URL/admin/realms/$REALM/users/profile | jq '[.attributes[].name]'"
echo "=========================================="
