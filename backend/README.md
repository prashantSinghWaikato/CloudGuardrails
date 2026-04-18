# CloudGuardrails Backend

The backend is the control plane for CloudGuardrails. It provides authentication, tenant-aware data access, cloud account onboarding, event ingestion, rule evaluation, violation tracking, remediation orchestration, and real-time messaging to connected clients.

## Purpose

This service is responsible for turning cloud activity into actionable security workflow records. It receives events, evaluates policy conditions, persists operational state, and exposes the APIs used by the web application and internal ingestion components.

## Core Responsibilities

- authenticate users and issue JWTs
- manage organizations and cloud accounts
- validate AWS account credentials before persistence
- ingest events from external and internal sources
- evaluate rules and create violations
- create, execute, retry, and reverify remediations
- publish notifications and real-time updates

## Technical Stack

- Java 17
- Spring Boot 3.5
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL
- Kafka
- AWS SDK v2
- WebSocket / STOMP

## High-Level Architecture

```text
Client / AWS Source
        |
        v
   REST Controllers
        |
        v
  Services / Rule Engine
        |
        v
 PostgreSQL + Kafka + WebSocket Messaging
```

## Package Layout

```text
src/main/java/com/cloud/guardrails
├── aws         AWS validation, ingestion, and remediation integrations
├── commons     Shared constants
├── config      Security, Kafka, rules, and WebSocket configuration
├── controller  External and internal API endpoints
├── dto         Request and response contracts
├── engine      Rule condition evaluation logic
├── entity      Persistence model
├── exception   Domain-specific exception handling
├── repository  JPA repositories
├── security    JWT and credential encryption support
└── service     Application services and orchestration
```

## Runtime Prerequisites

Required local dependencies:

- Java 17
- PostgreSQL on `localhost:5432`
- Kafka on `localhost:19092`

Default application values:

- application port: `8081`
- database: `cloud_guardrails`
- database user: `lio`
- database password: empty string

These values are defined in [src/main/resources/application.yaml](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/backend/src/main/resources/application.yaml:1).

## Configuration

### Application Configuration

Primary runtime configuration lives in:

- [src/main/resources/application.yaml](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/backend/src/main/resources/application.yaml:1)
- [src/main/resources/rules.yml](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/backend/src/main/resources/rules.yml:1)

### Environment Variables

- `GUARDRAILS_ENCRYPTION_SECRET`
  Encrypts stored cloud credentials.
- `GUARDRAILS_INGESTION_SECRET`
  Validates requests to the internal AWS ingestion endpoint.
- `GUARDRAILS_POLLING_ENABLED`
  Enables polling-based ingestion logic.

Example:

```bash
export GUARDRAILS_ENCRYPTION_SECRET="replace-with-a-long-random-secret"
export GUARDRAILS_INGESTION_SECRET="replace-with-a-long-random-secret"
export GUARDRAILS_POLLING_ENABLED=false
```

## Running Locally

Start the service:

```bash
./gradlew bootRun
```

Run tests:

```bash
./gradlew test
```

The service will be available at:

```text
http://localhost:8081
```

## Deployment on Render with Aiven Kafka

This backend is now configured to deploy cleanly to Render while using:

- Render Postgres for relational storage
- Aiven Free Kafka for managed Kafka
- base64-encoded environment variables for Kafka TLS materials

### Required Environment Variables

#### Database

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

#### Kafka

- `SPRING_KAFKA_BOOTSTRAP_SERVERS`
- `KAFKA_TOPIC`
- `KAFKA_GROUP_ID`
- `KAFKA_SECURITY_PROTOCOL`
- `KAFKA_SSL_KEYSTORE_TYPE`
- `KAFKA_SSL_KEYSTORE_LOCATION`
- `KAFKA_SSL_KEYSTORE_PASSWORD`
- `KAFKA_SSL_KEY_PASSWORD`
- `KAFKA_SSL_KEYSTORE_BASE64`
- `KAFKA_SSL_TRUSTSTORE_TYPE`
- `KAFKA_SSL_TRUSTSTORE_LOCATION`
- `KAFKA_SSL_TRUSTSTORE_PASSWORD`
- `KAFKA_SSL_TRUSTSTORE_BASE64`

#### Application Secrets

- `JWT_SECRET`
- `GUARDRAILS_ENCRYPTION_SECRET`
- `GUARDRAILS_INGESTION_SECRET`
- `CORS_ALLOWED_ORIGIN_PATTERNS`

### Render Binary Secret Handling

The deployment image decodes base64-encoded Kafka keystore and truststore values at container startup.

Set:

```text
KAFKA_SSL_KEYSTORE_LOCATION=/app/secrets/client.keystore.p12
KAFKA_SSL_TRUSTSTORE_LOCATION=/app/secrets/client.truststore.jks
KAFKA_SSL_KEYSTORE_BASE64=<base64 of client.keystore.p12>
KAFKA_SSL_TRUSTSTORE_BASE64=<base64 of client.truststore.jks>
KAFKA_SECURITY_PROTOCOL=SSL
```

Generate the base64 values with:

```bash
base64 < client.keystore.p12 | tr -d '\n'
base64 < client.truststore.jks | tr -d '\n'
```

### Aiven Java TLS Setup

According to Aiven's Java Kafka documentation, Java clients typically connect by creating:

- a PKCS12 keystore from `service.key` and `service.cert`
- a JKS truststore from `ca.pem`

Reference:

- https://aiven.io/docs/products/kafka/howto/keystore-truststore
- https://aiven.io/docs/products/kafka/howto/connect-with-java

### Deployment Assets

- Render blueprint: [../render.yaml](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/render.yaml:1)
- Backend image definition: [Dockerfile](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/backend/Dockerfile:1)
- Backend environment template: [.env.example](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/backend/.env.example:1)

## Database and Migrations

Database migrations are managed through Flyway and stored under:

- [src/main/resources/db/migration](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/backend/src/main/resources/db/migration:1)

Current migrations include:

- initial schema creation
- notifications support
- cloud account naming
- remediation audit fields
- remediation execution history

## API Surface

### Authentication

- `POST /auth/signup`
- `POST /auth/login`
- `GET /auth/me`

### Cloud Accounts

- `POST /accounts/validate`
- `POST /accounts`
- `GET /accounts`
- `PUT /accounts/{id}`
- `DELETE /accounts/{id}`

### Violations

- `GET /violations`
- `GET /violations/{id}`
- `GET /violations/filter`
- `GET /violations/search`
- `GET /violations/count`
- `GET /violations/recent`
- `PUT /violations/{id}/status`
- `POST /violations/{id}/remediate`

### Remediations

- `GET /remediations`
- `GET /remediations/{id}`
- `POST /remediations/{id}/approve`
- `POST /remediations/{id}/retry`
- `POST /remediations/{id}/reverify`

### Rules

- `GET /rules`
- `GET /rules/{id}`
- `PUT /rules/{id}`
- `PATCH /rules/{id}/enabled`

### Notifications

- `GET /notifications`
- `GET /notifications/unread-count`
- `PUT /notifications/{id}/read`
- `PUT /notifications/read-all`

### Internal Ingestion

- `POST /internal/aws/events`

The internal ingestion endpoint expects the header:

```http
X-Guardrails-Ingestion-Secret: <shared-secret>
```

## Authentication Model

The API issues JWTs from the authentication endpoints. Protected endpoints expect:

```http
Authorization: Bearer <jwt>
```

The current signup flow creates a new organization and an initial user with the `ADMIN` role.

## Example Local Workflow

### 1. Sign up

```http
POST /auth/signup
```

```json
{
  "name": "Lio",
  "email": "lio@example.com",
  "password": "password123",
  "organizationName": "Acme"
}
```

### 2. Validate an AWS account

```http
POST /accounts/validate
Authorization: Bearer <jwt>
```

```json
{
  "accountId": "123456789012",
  "provider": "AWS",
  "region": "us-east-1",
  "accessKey": "AKIA...",
  "secretKey": "..."
}
```

### 3. Save the account

```http
POST /accounts
Authorization: Bearer <jwt>
```

### 4. Submit an event

```http
POST /events
Authorization: Bearer <jwt>
```

```json
{
  "eventType": "AuthorizeSecurityGroupIngress",
  "resourceId": "sg-123456",
  "organizationId": 1,
  "cloudAccountId": 1,
  "payload": {
    "port": 22,
    "cidr": "0.0.0.0/0"
  }
}
```

### 5. Review operational state

- `GET /violations`
- `GET /remediations`
- `GET /notifications`

## Security Considerations

- JWT secret management should be externalized for non-local environments.
- AWS credential material should not be stored or shared outside approved secure workflows.
- The internal ingestion secret should be rotated and managed outside source control.
- Production deployments should place this service behind TLS termination and centralized monitoring.

## Related Assets

- API and service code: [src/main/java](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/backend/src/main/java:1)
- Runtime config: [src/main/resources](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/backend/src/main/resources:1)
- Tests: [src/test](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/backend/src/test:1)
- AWS ingestion support: [infra/aws-forwarder](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/backend/infra/aws-forwarder:1)
- Additional design notes: [docs/aws-push-ingestion.md](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/backend/docs/aws-push-ingestion.md:1)
