# Guardrails

Guardrails is a Spring Boot backend for multi-tenant cloud security monitoring. It ingests cloud activity, evaluates configurable rules, creates violations and remediations, and exposes APIs for account onboarding, authentication, event submission, and real-time updates.

## What It Does

- Authenticates users with JWT.
- Organizes data by organization and cloud account.
- Validates AWS accounts before saving them.
- Encrypts stored AWS credentials at rest.
- Ingests CloudTrail events from onboarded AWS accounts.
- Evaluates events against rules from `src/main/resources/rules.yml`.
- Creates violations and remediation records.
- Pushes violation/remediation updates over WebSocket/STOMP.

## Tech Stack

- Java 17
- Spring Boot 3.5
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL
- Kafka
- AWS SDK v2
- WebSocket/STOMP

## Project Structure

```text
src/main/java/com/cloud/guardrails
├── aws          AWS validation and ingestion
├── config       Spring, security, Kafka, and rule config
├── controller   REST endpoints
├── dto          Request/response models
├── engine       Rule condition evaluation
├── entity       JPA entities
├── exception    Structured API error handling
├── repository   Data access
├── security     JWT and credential encryption
└── service      Business logic
```

## Local Requirements

You need these running locally before the main flows work end to end:

- Java 17
- PostgreSQL on `localhost:5432`
- Kafka on `localhost:19092`

The app currently expects this database:

- database: `cloud_guardrails`
- username: `lio`
- password: empty string

Adjust `src/main/resources/application.yaml` if your local setup differs.

## Configuration

Current application config lives in [src/main/resources/application.yaml](/Users/lio/Downloads/guardrails/src/main/resources/application.yaml).

Important settings:

- `spring.datasource.*`: PostgreSQL connection
- `spring.kafka.bootstrap-servers`: Kafka broker
- `server.port`: API port, currently `8081`
- `jwt.secret`: JWT signing key
- `security.encryption.secret`: secret used to encrypt AWS credentials at rest

Recommended environment variable:

```bash
export GUARDRAILS_ENCRYPTION_SECRET="replace-this-with-a-long-random-secret"
```

## Running the App

Start PostgreSQL and Kafka first, then run:

```bash
./gradlew bootRun
```

Run tests:

```bash
./gradlew test
```

## API Overview

### Auth

- `POST /auth/signup`
- `POST /auth/login`
- `GET /auth/me`

### Cloud Accounts

- `POST /accounts/validate`
- `POST /accounts`
- `GET /accounts`
- `PUT /accounts/{id}`
- `DELETE /accounts/{id}`

### Events

- `POST /events`

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

## Authentication

Most endpoints require:

```http
Authorization: Bearer <jwt>
```

Get a token with signup or login, then reuse it in Postman or your frontend.

## Postman Setup Guide

### 1. Sign up

`POST http://localhost:8081/auth/signup`

```json
{
  "name": "Lio",
  "email": "lio@example.com",
  "password": "password123",
  "organizationName": "Acme"
}
```

Copy the JWT from the response.

### 2. Validate an AWS account before saving it

`POST http://localhost:8081/accounts/validate`

Headers:

```http
Authorization: Bearer <jwt>
Content-Type: application/json
```

Body:

```json
{
  "accountId": "123456789012",
  "provider": "AWS",
  "region": "us-east-1",
  "accessKey": "AKIA...",
  "secretKey": "..."
}
```

Expected behavior:

- valid credentials + matching account ID: success
- valid credentials + wrong account ID: failure
- invalid credentials: failure

### 3. Save the AWS account

`POST http://localhost:8081/accounts`

Use the same JSON body after validation succeeds.

### 4. Manually test rule evaluation

`POST http://localhost:8081/events`

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

Then inspect:

- `GET /violations`
- `GET /remediations`

## Error Response Format

The API now returns structured errors like:

```json
{
  "timestamp": "2026-04-10T05:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "AWS account ID does not match the supplied credentials",
  "path": "/accounts/validate"
}
```

## Rule Engine

Rules are defined in `src/main/resources/rules.yml`.

Each rule includes:

- `name`
- `eventType`
- `condition`
- `severity`
- `message`

### Supported Event Matching

- exact event type match, such as `RunInstances`
- wildcard event type with `*`

### Supported Condition Syntax

The current evaluator supports:

- equality: `port == 22`
- inequality: `user != 'root'`
- numeric comparison: `port >= 22`
- logical `&&`
- logical `||`
- contains: `userIdentity.arn contains 'admin'`
- exists: `requestParameters.bucketName exists`
- nested fields with dot notation: `requestParameters.bucketName == 'demo-bucket'`

Examples:

```yaml
condition: "port == 22 && cidr == '0.0.0.0/0'"
condition: "requestParameters.bucketName exists"
condition: "userIdentity.arn contains 'root' || sourceIp == 'unknown'"
```

## AWS Account Validation

When onboarding an AWS account, Guardrails calls AWS STS `GetCallerIdentity` using the provided credentials and verifies that the returned AWS account ID matches the account ID from the request.

That means:

- the request machine must have outbound internet access to AWS STS
- the credentials must be valid
- the region must be valid

## AWS Ingestion

The scheduler polls AWS CloudTrail for onboarded AWS accounts. Ingested events are:

- stored as internal `Event` rows
- evaluated against the rule set
- skipped if the same external CloudTrail event was already ingested for the same cloud account

## Security Notes

- AWS credentials are encrypted before being stored.
- Credentials are still long-lived secrets in your database; IAM role assumption would be stronger.
- JWT currently carries organization ID and allowed cloud account IDs.

## Current Gaps

Useful next improvements:

- integration tests for account validation and ingestion
- database-level unique constraint on `(cloud_account_id, external_event_id)`
- IAM role assumption instead of stored access keys
- richer remediation execution
- stronger auth semantics for `401` vs `403`

