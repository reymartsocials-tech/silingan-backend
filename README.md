# Silingan Backend

A Spring Boot backend application for community management with Keycloak authentication.

## Prerequisites

- **Java 21** - Required for building and running the application
- **Maven 3.9+** - For dependency management and building
- **Docker & Docker Compose** - For running Keycloak and PostgreSQL
- **Git** - For version control

## Quick Start Checklist

Follow these steps to set up the application locally:

- [ ] Clone the repository and navigate to the project directory
- [ ] Generate a JWT_SECRET key (see [JWT Secret Configuration](#jwt-secret-configuration))
- [ ] Export the JWT secret: `export JWT_SECRET="<generated-secret>"`
- [ ] Start Docker services: `docker-compose up -d`
- [ ] Wait for Keycloak to be ready (30-60 seconds)
- [ ] Create Keycloak realm `silingan-platform` (see [Realm Setup](#realm-setup))
- [ ] Create Keycloak clients (see [Client Configuration](#client-configuration))
- [ ] Create realm roles (see [Realm Roles](#realm-roles))
- [ ] Build the application: `mvn clean package`
- [ ] Run the application: `java -jar target/silingan-0.0.1.jar`
- [ ] Access Swagger UI: http://localhost:8082/swagger-ui/index.html
- [ ] Test self-service registration or OTP login endpoints


## Running with Docker Compose

### Step 1: Start PostgreSQL and Keycloak

The `docker-compose.yml` sets up two services:
- **PostgreSQL** (port 5432) - Database for Keycloak
- **Keycloak** (port 8080) - Authentication server

```bash
# Start the services
docker-compose up -d

# Check if services are running
docker-compose ps

# View logs
docker-compose logs -f keycloak
```

### Step 2: Wait for Keycloak to be Ready

Keycloak may take 30-60 seconds to start. Check when it's ready:

```bash
# Check Keycloak health
curl -f http://localhost:8080/realms/master
```

## Keycloak Configuration

### Realm Setup

The application expects a realm named **`silingan-platform`**. You need to create this realm in Keycloak.

#### Creating the Realm

1. Access Keycloak Admin Console: http://localhost:8080
2. Login with credentials:
   - Username: `admin`
   - Password: `admin`
3. Click **Realm Settings** → **Create Realm**
4. Enter `silingan-platform` as the realm name
5. Click **Create**

### Client Configuration

#### 1. Backend Client (for API authentication)

Create a client for the backend service:

1. Go to **Clients** → **Create Client**
2. **Client ID**: `silingan-backend`
3. **Name**: `silingan-backend`
4. **Client Authentication**: `ON`
5. **Authentication Flow**: `Service account roles`
6. Click **Next/Save**
7. Go to **Credentials** tab and copy the **Client Secret**
8. Place the client secret in the application.yaml under keycloak.client-secret

#### 2. API Client (for OAuth2 login)

Create a client for OAuth2 authentication:

1. Go to **Clients** → **Create Client**
2. **Client ID**: `silingan-api`
3. **Name**: `silingan-api`
4. Click **Next**
5. **Client Authentication**: `ON`
6. **Authentication Flow**: `Direct Access Grants`
7. Click **Next**
8. **Valid Redirect URIs**: `http://localhost:{FE_PORT}/login/oauth2/code/silingan`
9. **Web Origins**: `http://localhost:{FE_PORT}`
10. Click **Save**
11. Go to **Credentials** tab and copy the **Client Secret**
12. Place the client secret in the application.yaml under security.oauth2.client-secret
13. Update the application.yaml: security.oauth2.redirect-uri to `http://localhost:{FE_PORT}/login/oauth2/code/silingan`


#### 3. Client for Mobile App

For mobile app, create another client:
1. Go to **Clients** → **Create Client**
2. Client ID: `silingan-mobile`
3. Name: `silingan-mobile`
4. Client Authentication: `OFF`
5. Authentication Flow: `Standard Flow`
6. Valid redirect URIs: `silingan://callback`
6. Click **Next/Save**


### Realm Roles

Create the following realm roles in Keycloak:

1. Go to **Realm Settings** → **Roles** → **Add Role**
2. Create these roles:
   - `PLATFORM_ADMIN` - Full system access
   - `COMMUNITY_ADMIN` - Community management access
   - `RESIDENT` - Resident/user access

### Initial Users

Create a test user with appropriate roles:

1. Go to **Users** → **Add User**
2. Set username (e.g., `admin-user`)
3. Go to **Credentials** tab and set a password
4. uncheck "Temporary" to make the password permanent
4. Go to **Role Mappings** tab
5. Assign realm roles: `PLATFORM_ADMIN`

## JWT Secret Configuration

The application uses JWT tokens for authentication. A secure secret key (minimum 256 bits / 32 bytes) must be configured.

### Generate a JWT Secret

Generate a cryptographically secure secret:

```bash
# Using OpenSSL (recommended)
openssl rand -base64 32

# Using Python
python3 -c "import secrets; print(secrets.token_urlsafe(32))"

# Using Java
java -cp target/silingan-0.0.1.jar -c "import java.security.SecureRandom; import java.util.Base64; SecureRandom random = new SecureRandom(); byte[] bytes = new byte[32]; random.nextBytes(bytes); System.out.println(Base64.getEncoder().encodeToString(bytes));"
```

### Configure the Secret

Set the JWT secret as an environment variable:

```bash
# Development (local)
export JWT_SECRET="your-generated-secret-key-here"

# Or in application.yaml (dev only, never commit secrets!)
jwt:
  secret: your-generated-secret-key-here
  issuer: silingan-backend
  access-token-ttl-seconds: 3600
```

**Important:** Never commit actual secrets to version control. Use environment variables for production deployments.

## Running the Application

### Development Mode

The application uses H2 database for local development.

```bash
# Set JWT secret
export JWT_SECRET="your-generated-secret-key"

# Build the application
./mvnw clean package

# Run the application
./mvnw spring-boot:run
```

Or with Java directly:

```bash
# Build
mvn clean package

# Set environment variables
export JWT_SECRET="your-generated-secret-key"

# Run
java -jar target/silingan-0.0.1.jar
```

The application will start on port **8082** by default.


### Swagger UI

Once the application is running, you can access the Swagger UI for API documentation and testing:

 http://localhost:8082/swagger-ui/index.html

## Authentication Flows

### 1. Self-Service Registration with OTP

This flow allows users to register and obtain a JWT token in a single request.

#### Flow Overview

```
1. User provides mobile number + OTP verification
   ↓
2. Backend verifies OTP credentials
   ↓
3. User auto-assigned to default community (from Keycloak attributes)
   ↓
4. JWT token issued with communityId claim
   ↓
5. User can authenticate with JWT
```

#### API Endpoints

**Register with Self-Service:**
```http
POST /api/v1/auth/register/self-service
Content-Type: application/json

{
  "username": "jdelacruz",
  "email": "juan@example.com",
  "firstName": "Juan",
  "lastName": "Dela Cruz",
  "mobileNumber": "+639171234567",
  "password": "SecurePass123!",
  "enabled": true,
  "emailVerified": true,
  "communityId": "550e8400-e29b-41d4-a716-446655440000",
  "communityRole": "RESIDENT",
  "address": {
    "street": "123 Main St",
    "barangay": "Poblacion",
    "city": "Makati",
    "province": "Metro Manila",
    "region": 13,
    "postalCode": "1200",
    "country": "Philippines"
  }
}
```

**Response (Success):**
```json
{
  "success": true,
  "message": "User registered successfully",
  "username": "jdelacruz",
  "login": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "keycloakUserId": "abc123-def456-ghi789",
    "mobileNumber": "+639171234567",
    "roles": ["RESIDENT"]
  },
  "accessToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "roles": ["RESIDENT"]
}
```

### 2. OTP Login

Users with existing accounts can log in using OTP verification.

#### API Endpoints

**Request OTP (first step - external SMS provider):**
```http
POST /api/v1/otp/request
Content-Type: application/json

{
  "mobileNumber": "+639171234567"
}
```

**Verify OTP and Get JWT:**
```http
POST /api/v1/auth/login/otp
Content-Type: application/json

{
  "mobileNumber": "+639171234567",
  "otp": "123456"
}
```

**Response (Success):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "keycloakUserId": "abc123-def456-ghi789",
  "mobileNumber": "+639171234567",
  "roles": ["RESIDENT"]
}
```

### How JWT Tokens Work

#### Token Claims

Each JWT token contains the following claims:

| Claim | Type | Description |
|-------|------|-------------|
| `sub` | UUID | **Subject** - Application user ID (primary identifier) |
| `keycloakId` | String | Keycloak user ID (for Keycloak-specific operations) |
| `mobileNumber` | String | User's registered mobile number |
| `firstName` | String | User's first name |
| `lastName` | String | User's last name |
| `roles` | Array<String> | User's assigned realm roles (e.g., `["RESIDENT", "COMMUNITY_ADMIN"]`) |
| `communityId` | UUID | **Active community ID** (source of truth: Keycloak attributes) |
| `exp` | Timestamp | Token expiration time |
| `iat` | Timestamp | Token issued-at time |
| `iss` | String | Token issuer (e.g., `silingan-backend`) |
| `jti` | String | JWT ID (unique token identifier) |

#### Community Selection

The `communityId` claim is resolved from **Keycloak user attributes** at login/token-generation time:

1. **During user creation:** `communityId` is stored in Keycloak as a user attribute
2. **During OTP login:** Backend retrieves active `communityId` from Keycloak
3. **During community switch:** Keycloak attribute is updated as single source of truth
4. **In JWT:** Current active `communityId` is embedded in the token

**Example Keycloak Attribute:**
```json
{
  "communityId": ["550e8400-e29b-41d4-a716-446655440000"],
  "mobileNumber": ["+639171234567"],
  "phone_number": ["+639171234567"],
  "phone_number_verified": ["true"]
}
```

### 3. Community Switching

Users with multiple communities can switch their active community.

#### API Endpoint

**Switch Community:**
```http
POST /api/v1/communities/{communityId}/switch
Authorization: Bearer {accessToken}
```

**Effect:**
- Updates `communityId` attribute in Keycloak (single source of truth)
- Next JWT token will reflect the new `communityId`
- User remains authenticated with existing token until expiration

### Validation & Error Handling

#### Missing Community During Login

If a user has no active `communityId` in Keycloak:

```json
{
  "success": false,
  "errorType": "ValidationException",
  "message": "No active community selected for user"
}
```

**Resolution:** Ensure user has a valid `communityId` set in Keycloak attributes.

#### Invalid JWT Secret

If `JWT_SECRET` is missing or too short (< 256 bits):

```json
{
  "success": false,
  "errorType": "JwtEncodingException",
  "message": "An error occurred while attempting to encode the Jwt: Failed to create a JWS Signer -> The secret length must be at least 256 bits"
}
```

**Resolution:** Generate and set a valid JWT_SECRET (see JWT Secret Configuration above).

#### Invalid OTP

```json
{
  "success": false,
  "errorType": "InvalidOtpException",
  "message": "Invalid OTP"
}
```

**Resolution:** Verify user entered correct OTP before expiration (default: 5 minutes).



### Initial Data

The `data.sql` file seeds the database with:
- Sample addresses
- Two tenants (San Isidro LGU, Tower A Property Management)
- Two communities (Barangay San Isidro, Silingan Tower A)
- Sample users and their community roles
- Issue categories

Users in the seed data are pre-assigned to communities. When these users log in via OTP, their JWT will contain the `communityId` set in Keycloak.

## Community Staff RBAC

The backend supports **community-scoped RBAC** for staff accounts using a fixed catalogue of
predefined roles.

- Roles and the permission matrix are defined in code (`StaffRoleCatalog`) and are **read-only**
- Staff role assignments are stored per user per community (`user_community_staff_roles`)
- Custom roles, custom permissions, and per-community overrides are **not supported** for MVP

### Predefined Roles

| Role | Description |
|------|-------------|
| `COMMUNITY_ADMIN` | Full access to manage the community and staff (highest access) |
| `PMO_STAFF` | Can manage announcements and reports |
| `SECURITY_ADMIN` | Can view and manage security-related areas |
| `MAINTENANCE_ADMIN` | Can view and manage maintenance-related areas |
| `READ_ONLY_STAFF` | Can view information only |

### Permission Matrix

Each module resolves to one of `VIEW_AND_MANAGE`, `VIEW_ONLY`, or `NO_ACCESS`.

| Module | Community Admin | PMO Staff | Security Admin | Maintenance Admin | Read-Only Staff |
|--------|-----------------|-----------|----------------|-------------------|-----------------|
| Community | View + Manage | View Only | View Only | View Only | View Only |
| Residents | View + Manage | View Only | View Only | View Only | View Only |
| Staff | View + Manage | View Only | View Only | View Only | View Only |
| Announcements | View + Manage | View + Manage | View Only | View Only | View Only |
| Reports | View + Manage | View + Manage | View + Manage | View + Manage | View Only |
| Directory | View + Manage | View + Manage | View Only | View Only | View Only |
| Settings | View + Manage | No Access | No Access | No Access | No Access |

### Effective Permission Resolution

For each `(userId, communityId)` the effective permissions are exactly the permissions of the
user's **active** assigned predefined role. There are no overrides and no per-user grants, so what
the matrix shows is always what is enforced.

These effective permissions are loaded into `UserContext` and used by `@RequiresPermission`.

### RBAC APIs

#### Predefined Role Catalog & Permission Matrix (read-only)

- `GET /api/v1/communities/{communityId}/roles`
- `GET /api/v1/communities/{communityId}/roles/permissions`
- `GET /api/v1/rbac/permissions`

#### Staff Role Assignment

- `PUT /api/v1/communities/{communityId}/staff/{userId}/role`
- `GET /api/v1/communities/{communityId}/staff/{userId}/role`
- `GET /api/v1/communities/{communityId}/staff/{userId}/effective-permissions`

Example request:

```json
{
  "roleCode": "PMO_STAFF",
  "active": true
}
```

#### Staff Directory

- `GET /api/v1/communities/{communityId}/staff/directory`

#### Current User Capabilities

- `GET /api/v1/me/communities/{communityId}/capabilities`

Example response:

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "communityId": "550e8400-e29b-41d4-a716-446655440000",
  "roleCode": "PMO_STAFF",
  "permissions": ["ANNOUNCEMENT_VIEW", "ANNOUNCEMENT_MANAGE", "REPORT_VIEW", "REPORT_MANAGE"]
}
```

## Environment Variables Reference

Configure these environment variables for different deployment environments:

| Variable | Required | Description | Default | Example |
|----------|----------|-------------|---------|---------|
| `JWT_SECRET` | ✅ Yes | Secret key for JWT encoding (min 256 bits / 32 bytes) | None | `openssl rand -base64 32` |
| `JWT_ISSUER` | ❌ No | JWT issuer claim value | `silingan-backend` | `silingan-backend-prod` |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | ❌ No | JWT expiration time in seconds | `3600` | `7200` |
| `SERVER_PORT` | ❌ No | Application server port | `8082` | `8082` |
| `SPRING_PROFILES_ACTIVE` | ❌ No | Active Spring profile | `dev` | `prod`, `test` |
| `DATABASE_URL` | ❌ No* | Database connection URL | H2 (dev) | `jdbc:postgresql://localhost:5432/silingan` |
| `DATABASE_USERNAME` | ❌ No* | Database username | `sa` (H2) | `postgres` |
| `DATABASE_PASSWORD` | ❌ No* | Database password | empty | `password` |
| `KEYCLOAK_URL` | ❌ No* | Keycloak server URL | `http://localhost:8080` | `https://keycloak.example.com` |
| `KEYCLOAK_REALM` | ❌ No* | Keycloak realm name | `silingan-platform` | `silingan-platform` |
| `KEYCLOAK_CLIENT_ID` | ❌ No* | Keycloak backend client ID | `silingan-backend` | `silingan-backend` |
| `KEYCLOAK_CLIENT_SECRET` | ❌ No* | Keycloak backend client secret | None | (generated by Keycloak) |

*Only required for non-development environments (test, production)

## Troubleshooting

### Keycloak Connection Issues

If the application fails to connect to Keycloak:
1. Ensure Keycloak is running: `docker-compose ps`
2. Check Keycloak logs: `docker-compose logs keycloak`
3. Verify the realm `silingan-platform` exists
4. Verify the client `silingan-backend` exists with correct secret

### Database Issues

If you see database errors:
1. Check if `./data/silingan-db` files exist and are writable
2. For H2 issues, delete the `data/` directory to reset the database

### Port Conflicts

If port 8082 is in use:
```bash
# Run on a different port
SERVER_PORT=8083 ./mvnw spring-boot:run
```

## Additional Documentation

For detailed information about authentication flows and JWT setup, see:

- **[docs/AUTHENTICATION.md](docs/AUTHENTICATION.md)** - Comprehensive guide covering:
  - JWT Secret generation and configuration
  - OTP authentication flow (step-by-step)
  - Self-service registration process
  - JWT token structure and claims
  - Community switching and management
  - Error handling and troubleshooting
  - Testing procedures with examples

- **[docs/COMMUNITY_ID_ARCHITECTURE.md](docs/COMMUNITY_ID_ARCHITECTURE.md)** - Technical deep-dive on:
  - Why communityId is stored in Keycloak (single source of truth)
  - How communityId is resolved at login time
  - Community switching implementation
  - Data flow diagrams and code examples
  - Design decisions and rationale
  - Troubleshooting and future enhancements
