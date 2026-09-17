# Keycloak Setup for Silingan Backend

## Overview

This Keycloak setup provides:

1. **Platform Admin User** - A default admin user for the platform
2. **Silingan Backend Client** - Machine-to-Machine (M2M) client for backend services to manage Keycloak resources
3. **Default Community** - A sample community for testing
4. **Demo Data** - Sample data loaded on startup

## Default Credentials

### Platform Admin User
- **Username**: `platform_admin`
- **Password**: `admin123`
- **Email**: `admin@silingan.local`
- **Realm Role**: `PLATFORM_ADMIN`
- **Community ID**: `20000000-0000-0000-0000-000000000001`

### Keycloak Admin
- **Username**: `admin`
- **Password**: `admin`
- **URL**: `http://localhost:8080/admin`

### Silingan Backend Client (M2M)
- **Client ID**: `silingan-backend`
- **Client Secret**: `YVvWkbSHaxBM5o3l3Mqwr2jFYtBRmHK0`
- **Grant Type**: `client_credentials`

## Setting Up Backend Client Roles

The `silingan-backend` client needs **realm-management** roles to manage users, clients, and realms. These roles are assigned during setup.

### Option 1: Automatic Setup (Docker Compose)

When containers start with the `docker-compose.yml`, a script can be run to assign roles:

```bash
./keycloak/assign-backend-client-roles.sh
```

### Option 2: Manual Setup via Keycloak Admin UI

1. Go to `http://localhost:8080/admin`
2. Login with `admin` / `admin`
3. Navigate to **Realms > silingan-platform > Clients > silingan-backend**
4. Go to **Service Accounts Roles** tab
5. Click **Assign role**
6. From **realm-management** client, select:
   - `manage-users`
   - `manage-clients`
   - `manage-realm`
   - `query-users`
   - `query-clients`
   - `query-realms`
   - `view-clients`
   - `view-realm`

### Option 3: Keycloak Admin API

```bash
# Get admin token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password" | jq -r '.access_token')

# Get backend client ID
CLIENT_ID=$(curl -s -X GET "http://localhost:8080/admin/realms/silingan-platform/clients?clientId=silingan-backend" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id')

# Get realm-management client ID
REALM_MGMT=$(curl -s -X GET "http://localhost:8080/admin/realms/silingan-platform/clients?clientId=realm-management" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id')

# Get service account
SERVICE_ACCOUNT=$(curl -s -X GET "http://localhost:8080/admin/realms/silingan-platform/clients/$CLIENT_ID/service-account-user" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.id')

# Get roles
ROLES=$(curl -s -X GET "http://localhost:8080/admin/realms/silingan-platform/clients/$REALM_MGMT/roles" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -c '[.[] | select(.name | test("manage-users|manage-clients|manage-realm|query-users|query-clients|query-realms|view-clients|view-realm"))]')

# Assign roles
curl -X POST "http://localhost:8080/admin/realms/silingan-platform/users/$SERVICE_ACCOUNT/role-mappings/clients/$REALM_MGMT" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$ROLES"
```

## Using the Backend Client

### 1. Get Service Account Token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/silingan-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=silingan-backend" \
  -d "client_secret=YVvWkbSHaxBM5o3l3Mqwr2jFYtBRmHK0" \
  -d "grant_type=client_credentials" | jq -r '.access_token')

echo $TOKEN
```

### 2. Use Token for Admin API Calls

All requests use the Keycloak Admin API with the service account token:

#### Create User
```bash
curl -X POST http://localhost:8080/admin/realms/silingan-platform/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john.doe",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "enabled": true,
    "credentials": [{
      "type": "password",
      "value": "SecurePass123!",
      "temporary": true
    }]
  }'
```

#### Query Users
```bash
curl -X GET "http://localhost:8080/admin/realms/silingan-platform/users?search=john" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
```

#### Get User by ID
```bash
curl -X GET "http://localhost:8080/admin/realms/silingan-platform/users/{userId}" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
```

#### Update User
```bash
curl -X PUT "http://localhost:8080/admin/realms/silingan-platform/users/{userId}" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Jane",
    "lastName": "Smith",
    "email": "jane@example.com"
  }'
```

#### Delete User
```bash
curl -X DELETE "http://localhost:8080/admin/realms/silingan-platform/users/{userId}" \
  -H "Authorization: Bearer $TOKEN"
```

#### Assign Realm Role to User
```bash
# First get the role ID
ROLE=$(curl -s -X GET "http://localhost:8080/admin/realms/silingan-platform/roles/PLATFORM_ADMIN" \
  -H "Authorization: Bearer $TOKEN" | jq '.')

curl -X POST "http://localhost:8080/admin/realms/silingan-platform/users/{userId}/role-mappings/realm" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "[$ROLE]"
```

#### Set User Attributes
```bash
curl -X PUT "http://localhost:8080/admin/realms/silingan-platform/users/{userId}" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "attributes": {
      "communityId": "20000000-0000-0000-0000-000000000001"
    }
  }'
```

## JWT Token Structure

### User Token (via silingan-api client)
```json
{
  "sub": "kc-platform-admin-001",
  "preferred_username": "platform_admin",
  "email": "admin@silingan.local",
  "name": "Platform Admin",
  "communityId": "20000000-0000-0000-0000-000000000001",
  "realm_access": {
    "roles": ["PLATFORM_ADMIN"]
  }
}
```

### Service Account Token (via silingan-backend client)
```json
{
  "sub": "378b44bf-79ab-4b6b-9d88-81028541f177",
  "preferred_username": "service-account-silingan-backend",
  "client_id": "silingan-backend",
  "resource_access": {
    "realm-management": {
      "roles": [
        "manage-users",
        "query-users",
        "manage-clients",
        "query-clients",
        "manage-realm",
        "query-realms",
        "view-clients",
        "view-realm"
      ]
    }
  }
}
```

## Integration with Silingan Backend

The backend can use the `keycloak-admin-client` library (already in `pom.xml`) to make API calls:

```java
// Already configured in application.yaml
keycloak:
  url: http://localhost:8080
  realm: silingan-platform
  client-id: silingan-backend
  client-secret: YVvWkbSHaxBM5o3l3Mqwr2jFYtBRmHK0
```

## Local Development

When running locally with the `local` profile:

1. **Flyway is disabled** - Hibernate creates/drops the schema
2. **Demo data loads automatically** from `db/data/demo-data.sql`
3. **Keycloak imports realm** from `keycloak/import/silingan-platform-realm.json`

### Start Everything

```bash
# Full clean rebuild
docker-compose down -v && docker-compose up -d

# Assign backend client roles (if needed)
./keycloak/assign-backend-client-roles.sh
```

### Access Points

- **Keycloak Admin**: `http://localhost:8080/admin`
- **Keycloak OAuth**: `http://localhost:8080/realms/silingan-platform`
- **Silingan Backend**: `http://localhost:8082`
- **Database**: `localhost:5433` (silingan DB)
- **Keycloak DB**: `localhost:5432`

## Files

- `docker-compose.yml` - Main compose file with all services
- `Dockerfile` - Backend application image
- `keycloak/import/silingan-platform-realm.json` - Realm configuration and users
- `keycloak/assign-backend-client-roles.sh` - Script to assign client roles
- `src/main/resources/db/data/demo-data.sql` - Demo data for local development
- `src/main/resources/application.yaml` - Application configuration (local profile)

## Troubleshooting

### Keycloak won't start
- Check logs: `docker-compose logs keycloak`
- Ensure JSON is valid: `jq . keycloak/import/silingan-platform-realm.json`

### Token doesn't have expected claims
- Verify protocol mappers in client settings
- Check that scopes include `openid`, `profile`, `email`

### Backend can't access Keycloak Admin API
- Ensure service account has been assigned roles
- Check that `client_secret` matches in configuration
- Run: `./keycloak/assign-backend-client-roles.sh`

### Demo data not loading
- Ensure `spring.profiles.active=local` is set
- Check that Hibernate is set to `ddl-auto: create-drop`
- Verify `flyway.enabled: false` in local profile
