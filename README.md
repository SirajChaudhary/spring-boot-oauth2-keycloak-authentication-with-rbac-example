# Spring Boot + Keycloak OAuth2 Authentication with RBAC

A complete example showing how to secure a Spring Boot REST API using **Keycloak** (OIDC / OAuth2) with **role-based access control (RBAC)**. The project demonstrates:

- How to run Keycloak using Docker Compose and import a realm automatically.
- How to configure Keycloak clients, realm roles and users via `keycloak-realm.json`.
- How to secure Spring Boot endpoints using `spring-boot-starter-oauth2-resource-server` and method-level RBAC (`@PreAuthorize`).
- A Postman collection to request tokens and call protected endpoints (ADMIN vs USER).

---

## Tech stack & versions

- Java: **21**
- Spring Boot: **3.5.6** (parent in `pom.xml`)
- Keycloak: **26.1.1** (docker image `quay.io/keycloak/keycloak:26.1.1`)
- Docker Compose: **v3.9** format
- Maven: 3.x (compatible with Spring Boot)
- Spring modules: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-client`, `spring-boot-starter-oauth2-resource-server`, `springdoc-openapi-starter-webmvc-ui`
- Postman: any modern Postman to import the collection provided

---

## What we built

- A tiny in-memory `EmployeeController` with endpoints under `/api/v1/employees`.
- RBAC rules:
  - `GET /api/v1/employees` and `GET /api/v1/employees/{id}` → accessible to `USER` and `ADMIN` roles.
  - `POST`, `PUT`, `DELETE` → accessible only to `ADMIN`.
- Keycloak realm `demo` with two realm roles: `ADMIN` and `USER`.
- Two users imported with the realm JSON:
  - `prasad` (ADMIN) — `admin123`
  - `siraj` (USER) — `user123`

We use Keycloak's **Direct Access Grants** (resource owner password credentials) to obtain tokens from Postman or `curl` for the demo. The Spring Boot app validates incoming Keycloak JWT access tokens and maps Keycloak realm roles to Spring authorities (`ROLE_*`) so `@PreAuthorize` checks work.

---

## Project Structure

```
├── docker-compose.yml
├── keycloak-realm.json
├── pom.xml
├── README.md
└── src/
    └── main/java/com/example/
        ├── SpringBootOauth2KeycloakRbacExampleApplication.java
        ├── config/SecurityConfig.java
        ├── controller/EmployeeController.java
        └── dto/Employee.java
```

- `docker-compose.yml` — starts Keycloak and mounts `keycloak-realm.json` for automatic import.
- `keycloak-realm.json` — describes the realm `demo`, client `spring-app`, roles, and users.
- `SecurityConfig` — configures resource server JWT validation and maps Keycloak roles to Spring `ROLE_*` authorities.
- `EmployeeController` — shows method-level `@PreAuthorize` checks.

---

## Explain `docker-compose.yml`

Here is the `docker-compose.yml` used in this example (excerpt):

```yaml
version: "3.9"

services:
  keycloak:
    image: quay.io/keycloak/keycloak:26.1.1
    container_name: keycloak
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
      KC_DB: dev-file
      KC_IMPORT: /opt/keycloak/data/import/keycloak-realm.json
    volumes:
      - ./keycloak-realm.json:/opt/keycloak/data/import/keycloak-realm.json
    ports:
      - "8080:8080"
    command: start-dev --import-realm
```

**What each part does:**

- `image` — the Keycloak server image (v26.1.1).
- `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD` — create the initial Keycloak admin user so you can log into the Admin Console.
- `KC_DB: dev-file` — dev-mode file-based persistence (suitable for local development only).
- `KC_IMPORT` — tells Keycloak where to find a realm JSON to import on startup.
- `volumes` — mounts your local `keycloak-realm.json` into the container so Keycloak can import it.
- `ports` — exposes Keycloak on `localhost:8080`.
- `command: start-dev --import-realm` — runs Keycloak in development mode and imports the realm file.

**Notes:**
- `dev-file` is for development; do not use it in production. For production, configure a real DB (Postgres).
- If you re-import the realm after Keycloak already created its internal storage, you may need to remove the container / data or run `docker compose down -v` to wipe volumes so the import runs cleanly.

---

## Explain `keycloak-realm.json`

This JSON defines everything Keycloak needs to create the realm and the objects inside it. Key fields used in this example:

- `realm`: The name of the realm (`demo`).
- `clients`: List of client applications. The `spring-app` client includes:
  - `clientId`: `spring-app`
  - `secret`: `spring-app-secret` (client is **confidential**)
  - `protocol`: `openid-connect`
  - `redirectUris`: used for standard auth-code flows when doing browser login
  - `publicClient`: `false` → confidential client (requires secret)
  - `standardFlowEnabled`: `true` → authorization code flow
  - `directAccessGrantsEnabled`: `true` → **enables password grant (`grant_type=password`)**
  - `defaultClientScopes` and `optionalClientScopes` — controls which claims are placed in tokens

- `roles`: realm roles created by the realm import. We created `ADMIN` and `USER`.

- `users`: two users (`prasad`, `siraj`) with:
  - `enabled: true` — user is active
  - `emailVerified: true` — avoids “Account is not fully set up” errors
  - `credentials` with type `password`, `temporary: false` — permanent password
  - `realmRoles`: assigns `ADMIN` or `USER`
  - `requiredActions: []` — ensures no required action is pending

**Important settings for password grant & token use**:
- `directAccessGrantsEnabled: true` (client must allow direct access grants for password flow)
- `emailVerified: true` and `requiredActions: []` (users must be fully setup to allow direct login)

---

## How the Spring Boot side is configured (summary)

- `application.properties` contains the resource server issuer URI which points to Keycloak's realm metadata, e.g.:

```
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/demo
```

- Spring Security config (`SecurityConfig`) does the following:
  - Enables method-level security (`@EnableMethodSecurity`) so `@PreAuthorize` works.
  - Configures the application as an **OAuth2 Resource Server** that validates Keycloak JWTs.
  - Uses a custom `JwtAuthenticationConverter` to extract `realm_access.roles` from the JWT and convert them to Spring `GrantedAuthority`s with `ROLE_` prefix (so `hasRole('ADMIN')` matches `ROLE_ADMIN`).

This approach means the Spring app does **not** store usernames/passwords locally — Keycloak handles authentication and issues JWTs that Spring validates.

---

## Step-by-step: run the example locally

### Prerequisites

- Git
- Docker & Docker Compose
- Java 21 (JDK)
- Maven
- Postman (or `curl`)

### Steps

**Step 1:** Clone the repository

```bash
git clone <this-repo-url>
cd spring-boot-oauth2-keycloak-authentication-with-rbac-example
```

**Step 2:** Start Keycloak using Docker Compose

```bash
docker compose up -d
```

Open the Keycloak admin console in your browser: `http://localhost:8080/` and log in using:

```
username: admin
password: admin
```

(If the admin console is not yet accessible, inspect logs: `docker logs -f keycloak`.)

> If you re-run the container and need to force re-import of `keycloak-realm.json`, run:
>
> ```bash
> docker compose down -v
> docker compose up -d
> ```

**Step 3:** Build and run the Spring Boot application

```bash
mvn clean package
mvn spring-boot:run
```

The app runs on `http://localhost:8081`.

**Step 4:** Import the Postman collection

Import the provided `postman_collection.json` (in the project root) into Postman.

**Step 5:** Request an access token

Use Postman or `curl` to request a token for one of the users. Token endpoint:

```
POST http://localhost:8080/realms/demo/protocol/openid-connect/token
```

Body (x-www-form-urlencoded):

- `grant_type`: `password`
- `client_id`: `spring-app`
- `client_secret`: `spring-app-secret`
- `username`: `prasad` or `siraj`
- `password`: `admin123` (for prasad) or `user123` (for siraj)
- `scope`: `openid`

Example `curl` for `prasad` (ADMIN):

```bash
curl -X POST "http://localhost:8080/realms/demo/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=spring-app" \
  -d "client_secret=spring-app-secret" \
  -d "username=prasad" \
  -d "password=admin123" \
  -d "scope=openid"
```

**Step 6:** Call the protected API

Add the `Authorization: Bearer <ACCESS_TOKEN>` header to your request and call the endpoints, for example:

```
GET http://localhost:8081/api/v1/employees
```

- With `prasad` (ADMIN token) you can call all endpoints including POST/PUT/DELETE.
- With `siraj` (USER token) you can only call GET endpoints. POST/PUT/DELETE will return `403 Forbidden`.



---

There is a `postman_collection.json` in the project root which you can import into Postman and run the APIs.

---

## Why use Keycloak for this scenario?

Keycloak provides a production-grade identity and access management solution that lets you offload authentication and authorization from your application. It is especially useful in scenarios where you do not want to manage user credentials, sessions, or token issuance inside each service. Key benefits showcased by this example:

- **Centralized authentication & authorization** — Keycloak handles login, password management, account recovery, and user lifecycle so your application code can remain focused on business logic rather than user management.
- **Standards-compliant (OAuth2 / OpenID Connect)** — integrate easily with many clients, libraries, and social identity providers without custom protocol work.
- **Role-based access control (RBAC) and token-based authorization** — roles and permissions are embedded in JWTs so downstream services can remain stateless and simply validate tokens.
- **Single Sign-On (SSO) and session management** — authenticate once and access multiple applications; Keycloak manages sessions, logout, and token revocation.
- **Extensibility and federation** — connect to LDAP/AD, enable social logins, and use mappers and client scopes to control token contents without changing service code.
- **Admin UI and user self-service** — powerful Admin Console for managing realms, clients, roles, and users; optional user-facing features (password reset, account management) avoid building those flows in-app.
- **Scalability and security features** — supports clustering, secure token issuance, refresh tokens, and fine-grained policies for production deployments.

In short, Keycloak lets you centralize and standardize authentication/authorization, speed up development, and improve security posture by relying on a mature IAM system.

---

## Important notes & best practices

- This demo uses **dev-file** Keycloak persistence — it's for **development only**. For any persistent or production setup, use a persistent DB (Postgres) and secure the admin user/password.
- Do not use the **password grant** in public/production scenarios for third-party clients — prefer Authorization Code Flow with PKCE.
- Keep your `client_secret` and admin credentials out of source control in real projects.
- For production: enable TLS, configure strong client secrets, and consider Multi-Factor Authentication where needed.

---

## Files to review / customize

- `application.properties` — set `spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/demo`
- `SecurityConfig` — see JWT role mapping logic
- `keycloak-realm.json` — edit users / roles / client settings to match your needs

---

## License

Free Software, by [Siraj Chaudhary](https://www.linkedin.com/in/sirajchaudhary/)
