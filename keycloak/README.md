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

---

## Staff Invitation Emails (custom email theme)

Staff invitations reuse Keycloak's built-in `executeActionsEmail()` flow. No custom
SPI and no extra realm is needed: the Silingan staff role travels to the email
template as a **user attribute**, and the theme branches on it with FreeMarker.

### Why a user attribute and not a realm role

`StaffRoleCode` (`COMMUNITY_ADMIN`, `PMO_STAFF`, `SECURITY_ADMIN`,
`MAINTENANCE_ADMIN`, `READ_ONLY_STAFF`) is an application concept stored in the
Silingan database. These are deliberately **not** Keycloak realm roles, so the
backend writes them onto the Keycloak user right before sending the email.

### Theme folder structure

```
keycloak/themes/my-community-theme/
└── email/
    ├── theme.properties                 # parent=base, locales=en
    ├── messages/
    │   └── messages_en.properties       # subject + requiredAction labels only
    ├── html/
    │   ├── executeActions.ftl           # router: the ONLY emailLayout wrapper
    │   ├── invitation-context.ftl       # safe attribute helpers (attr/greeting/expiry)
    │   ├── staff-invitation.ftl         # macro lib, conditionals on roleCode
    │   └── community-invitation.ftl     # macro lib, resident fallback
    └── text/
        ├── executeActions.ftl           # same routing, plain text
        ├── invitation-context.ftl
        ├── staff-invitation.ftl
        └── community-invitation.ftl
```

`template.ftl` (the `emailLayout` macro) is inherited from the `base` theme and
exists only under `html/`, which is why the `text/` router renders the partials
directly.

### Attributes read by the theme

| Attribute         | Example                                | Purpose                                 |
|-------------------|----------------------------------------|-----------------------------------------|
| `invitationType`  | `STAFF` / `RESIDENT`                   | Chooses staff vs resident content       |
| `roleCode`        | `SECURITY_ADMIN`                       | Selects the role specific body          |
| `roleDisplayName` | `Security Admin`                       | Human readable label                    |
| `communityName`   | `Sunrise Village`                      | Rendered in the body                    |
| `communityId`     | `1111...`                              | Traceability only                       |

They are produced by `InvitationEmailContext#toKeycloakAttributes()` in the
backend and declared in `keycloak/config/silingan-user-profile.json`.

### Required realm configuration

Keycloak 26 uses the declarative user profile and **silently discards unknown
("unmanaged") user attributes**. Without the config below, `roleCode` is never
stored and every invitation falls back to the default template.

For a brand new realm, `keycloak/import/silingan-platform-realm.json` already
contains the user profile, SMTP settings and
`actionTokenGeneratedByAdminLifespan`. Note that `--import-realm` **skips realms
that already exist**, so for an existing realm apply the same settings over the
Admin REST API:

```bash
ADMIN_USER=admin ADMIN_PASS=<kc-admin-password> ./keycloak/apply-invitation-config.sh
```

> Only realm JSON files may live in `keycloak/import/`. Keycloak tries to import
> every file in that directory as a realm, which is why the user profile
> definition lives in `keycloak/config/`.

### Local email testing (Mailpit)

Mailpit is only a **local SMTP sink** used so invitations can be inspected
without sending real mail. Nothing about the theme depends on it.

```bash
docker compose up -d
# Inbox UI: http://localhost:8025
```

### Sending through a real provider (Gmail, SES, SendGrid, ...)

The messages Keycloak produces are ordinary multipart (text + HTML) emails, so
they render the same in Gmail, Outlook or any other client. Only the realm SMTP
settings change - no template change is required.

```bash
SMTP_HOST=smtp.gmail.com \
SMTP_PORT=587 \
SMTP_STARTTLS=true \
SMTP_AUTH=true \
SMTP_USER='you@gmail.com' \
SMTP_PASSWORD='xxxx xxxx xxxx xxxx' \
SMTP_FROM='you@gmail.com' \
SMTP_FROM_DISPLAY_NAME='Silingan' \
ADMIN_USER=admin ADMIN_PASS=<kc-admin-password> \
./keycloak/apply-invitation-config.sh
```

Gmail notes:
- Use a **Google App Password** (requires 2FA); the normal account password is
  rejected.
- `SMTP_FROM` must be the authenticated account or a verified alias, otherwise
  Gmail rewrites or rejects the sender.
- Port `587` with `SMTP_STARTTLS=true`, or port `465` with `SMTP_SSL=true`.
- Gmail is fine for testing but rate limited; use SES/SendGrid/Postmark for
  production volume.

You can also set this in the admin console under
**Realm settings -> Email**, then use *Test connection*.

### Deploying the theme to your Keycloak

How you ship the theme depends on how Keycloak runs.

**1. docker-compose (local) - nothing to build.**
`docker-compose.yml` bind-mounts `./keycloak/themes` into the container, so
template edits are picked up on restart:

```bash
docker compose restart keycloak
```

**2. `keycloak/Dockerfile` (deployed image) - rebuild required.**
That Dockerfile `COPY`s `themes/`, `providers/` and `import/` at **build time**
and runs `kc.sh build`, so a running image does not see your changes until it is
rebuilt and redeployed:

```bash
docker build -t <your-registry>/silingan-keycloak:latest ./keycloak
docker push <your-registry>/silingan-keycloak:latest
# then redeploy/restart the service using that image
```

In both cases the realm-side settings (user profile attributes, SMTP, token
lifespan) are **data, not image content**. A rebuilt image will not apply them to
an existing realm, so run this once per environment:

```bash
KEYCLOAK_URL=https://<your-keycloak> REALM=silingan-platform \
ADMIN_USER=admin ADMIN_PASS=<kc-admin-password> \
./keycloak/apply-invitation-config.sh
```

### Iterating on templates

Templates are bind-mounted into the container, and `docker-compose.theme-dev.yml`
disables theme caching:

```bash
docker compose -f docker-compose.yml -f docker-compose.theme-dev.yml up -d
```

### Pitfalls to avoid

- **Never index an attribute directly** (`user.attributes.roleCode[0]`).
  `ProfileBean.getAttributes()` returns `Map<String, String>`, so `[0]` performs
  *string slicing* and yields `"P"` — the comparison silently never matches.
  Use the `attr()` helper in `invitation-context.ftl` instead.
- **Wrap `emailLayout` exactly once.** Only `executeActions.ftl` applies it; the
  partials are macro libraries that emit body content.
- **The subject cannot depend on `roleCode`.** Keycloak resolves
  `executeActionsSubject` from the message bundle before the template runs and
  without access to user attributes, so the subject is kept role neutral. Varying
  it would require a custom `EmailTemplateProvider` SPI.
