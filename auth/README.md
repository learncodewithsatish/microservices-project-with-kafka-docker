# Auth Service

A standalone authentication microservice built with Spring Boot. It exposes two endpoints — user registration and JWT-based login — intended to be reused as the identity/auth layer in a microservice architecture.

## Tech Stack

| Concern            | Choice                                              |
|---------------------|------------------------------------------------------|
| Language / Runtime  | Java 17                                              |
| Framework           | Spring Boot 4.1.1 (Spring Framework 7 / Spring Security 7) |
| Persistence         | Spring Data JPA + Hibernate, MySQL (runtime), H2 (test) |
| Auth                | Spring Security, stateless JWT (HS512 via Nimbus JOSE+JWT) |
| Validation          | Jakarta Bean Validation (`spring-boot-starter-validation`) |
| Boilerplate         | Lombok                                               |
| Build               | Maven (wrapper included, no local Maven install needed) |

## Project Structure

```
src/main/java/com/auth
├── AuthApplication.java          Application entry point (@ConfigurationPropertiesScan)
├── config
│   ├── SecurityConfig.java       Security filter chain, PasswordEncoder, AuthenticationManager
│   ├── JwtProperties.java        Type-safe binding for app.jwt.* properties
│   └── JpaConfig.java            Enables JPA auditing (createdAt/updatedAt)
├── controller
│   └── AuthController.java       POST /api/auth/register, POST /api/auth/login
├── dto
│   ├── request
│   │   ├── RegisterRequest.java
│   │   └── LoginRequest.java
│   └── response
│       ├── UserResponse.java
│       └── AuthResponse.java
├── entity
│   └── User.java
├── enums
│   └── Role.java
├── exception
│   ├── ApiError.java                    Standard error response shape
│   ├── GlobalExceptionHandler.java      @RestControllerAdvice — central exception handling
│   ├── JwtAuthenticationEntryPoint.java 401 handler for unauthenticated requests
│   ├── CustomAccessDeniedHandler.java   403 handler for authenticated-but-forbidden requests
│   ├── UserAlreadyExistsException.java
│   ├── InvalidCredentialsException.java
│   └── ResourceNotFoundException.java
├── repository
│   └── UserRepository.java
├── security
│   ├── JwtService.java                  Token generation / parsing / validation
│   ├── JwtAuthenticationFilter.java      Reads Authorization header, populates SecurityContext
│   └── CustomUserDetailsService.java
└── service
    ├── AuthService.java
    └── impl
        └── AuthServiceImpl.java
```

## Prerequisites

- JDK 17+
- MySQL 8.x running locally (or reachable), **or** just run the test suite, which uses an in-memory H2 database and needs nothing extra.

No local Maven install is required — use the bundled wrapper (`mvnw` / `mvnw.cmd`).

## Configuration

All configuration lives in `src/main/resources/application.properties`. Everything is overridable via environment variables or `--flag` args at startup.

```properties
spring.application.name=auth

# Datasource
spring.datasource.url=jdbc:mysql://localhost:3306/auth_db?useSSL=false&serverTimezone=UTC&createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=root

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.open-in-view=false

# JWT
app.jwt.secret=${JWT_SECRET:<base64-encoded default>}
app.jwt.expiration-ms=${JWT_EXPIRATION_MS:3600000}
```

| Property                | Description                                   | Default        |
|--------------------------|-----------------------------------------------|----------------|
| `spring.datasource.*`    | MySQL connection details                       | local MySQL, `auth_db` |
| `app.jwt.secret`         | Base64-encoded HMAC signing key (min 256-bit) | dev-only value baked into properties |
| `app.jwt.expiration-ms`  | Token lifetime in milliseconds                 | `3600000` (1 hour) |

> **Security note:** the checked-in `app.jwt.secret` default is fine for local development only. For any shared, staging, or production environment, set the `JWT_SECRET` environment variable to a secret pulled from a proper secrets manager (e.g. AWS Secrets Manager / Parameter Store) rather than relying on the value in source control.

## Running the Service

```bash
# from the project root (where pom.xml lives)
./mvnw.cmd spring-boot:run        # Windows
./mvnw spring-boot:run            # macOS / Linux
```

The service starts on `http://localhost:8080` by default.

To point at a different database or secret without editing files:

```bash
./mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:mysql://localhost:3306/auth_db --JWT_SECRET=<your-secret>"
```

## Building

```bash
./mvnw.cmd clean package
java -jar target/auth-0.0.1-SNAPSHOT.jar
```

## Running Tests

```bash
./mvnw.cmd test
```

Tests run against an in-memory H2 database (configured in `src/test/resources/application.properties`) — no external database needed. This includes:

- `AuthApplicationTests` — verifies the full Spring context loads.
- `AuthControllerTest` — end-to-end tests through the real HTTP layer: successful register → login round trip, duplicate-email conflict, wrong-password rejection, and payload validation errors.

## API Reference

Base path: `/api/auth`

### Register

```
POST /api/auth/register
Content-Type: application/json
```

Request body:

```json
{
  "fullName": "Jane Doe",
  "email": "jane.doe@example.com",
  "password": "SecurePass1"
}
```

| Field      | Rules                                  |
|------------|------------------------------------------|
| `fullName` | required, max 100 chars                  |
| `email`    | required, valid email format, max 150 chars |
| `password` | required, 8–100 chars                    |

Success response — `201 Created`:

```json
{
  "id": 1,
  "fullName": "Jane Doe",
  "email": "jane.doe@example.com",
  "role": "USER",
  "createdAt": "2026-09-15T10:30:00Z"
}
```

Failure — `409 Conflict` if the email is already registered.

### Login

```
POST /api/auth/login
Content-Type: application/json
```

Request body:

```json
{
  "email": "jane.doe@example.com",
  "password": "SecurePass1"
}
```

Success response — `200 OK`:

```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "expiresInMs": 3600000
}
```

Failure — `401 Unauthorized` for a wrong email/password combination.

Use the token on subsequent requests to any protected endpoint:

```
Authorization: Bearer <accessToken>
```

## Error Response Shape

All errors — validation failures, conflicts, auth failures, and unhandled exceptions — return a consistent body via the global exception handler:

```json
{
  "timestamp": "2026-09-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields",
  "path": "/api/auth/register",
  "validationErrors": {
    "email": "Email must be a valid email address",
    "password": "Password must be between 8 and 100 characters"
  }
}
```

`validationErrors` is only present for field-validation failures; other error types omit it.

| Status | Scenario                                              |
|--------|--------------------------------------------------------|
| 400    | Malformed JSON body, or Bean Validation failures        |
| 401    | Bad credentials on login, or missing/invalid JWT on a protected endpoint |
| 403    | Authenticated but lacking permission for the resource   |
| 404    | Requested resource not found                            |
| 409    | Email already registered, or a unique-constraint race at the DB level |
| 500    | Unhandled server-side error                              |

## Security Model

- Passwords are hashed with BCrypt (`PasswordEncoder` bean) before storage — plaintext passwords are never persisted.
- Login authenticates through Spring Security's `AuthenticationManager`, backed by `CustomUserDetailsService`.
- On success, `JwtService` issues an HS512-signed JWT containing the user's email as subject and their role as a custom claim.
- Every subsequent request passes through `JwtAuthenticationFilter`, which validates the `Authorization: Bearer <token>` header and populates the `SecurityContext` — no server-side session state is kept (`SessionCreationPolicy.STATELESS`).
- `/api/auth/register` and `/api/auth/login` are the only endpoints permitted without a token; everything else requires authentication by default.


# Docker Commands for Auth Service

## Step 1: Build Docker Image

```bash
docker build -t auth-service:latest .
```
Now your docker image will be automatically loaded into your docker desktop, Just simply run that Image, Docker Container will be automatically created.
When you click on Run Icon then there will option to configure variables. Do those changes then run