# CloudGuardrails

CloudGuardrails is a full-stack cloud security operations platform designed to detect policy violations, manage remediation workflows, and provide a centralized operational view of cloud risk. This repository contains the complete application stack, including the Spring Boot backend and the React-based frontend.

## Executive Summary

The platform is built around a guardrail operating model:

- onboard cloud accounts into a tenant-aware control plane
- ingest cloud activity and normalize it into internal events
- evaluate events against configurable security rules
- create violations and remediation records
- surface current posture, workflow status, and notifications through a web UI

The current implementation is centered on AWS and supports both API-driven operations and real-time updates to the frontend.

## Repository Structure

```text
CloudGuardrails/
├── backend/    Spring Boot API, rule engine, persistence, ingestion, remediation
├── frontend/   React + Vite operator console
├── .env.example
├── .editorconfig
├── .gitignore
└── README.md
```

## Platform Architecture

### Backend

The backend provides:

- authentication and tenant-aware access control
- cloud account onboarding and AWS account validation
- event ingestion and rule evaluation
- violation lifecycle management
- remediation approval, execution, retry, and reverification
- notification APIs and WebSocket/STOMP updates

### Frontend

The frontend provides:

- authentication flows for signup and login
- dashboard views for exposure and remediation posture
- cloud account onboarding and validation workflows
- violation triage and remediation visibility
- rules management and operational monitoring

## Technology Stack

### Backend

- Java 17
- Spring Boot 3.5
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL
- Kafka
- AWS SDK v2
- WebSocket / STOMP

### Frontend

- React 19
- TypeScript
- Vite
- Tailwind CSS 4
- Recharts
- SockJS / STOMP client

## Local Development Prerequisites

To run the full platform locally, ensure the following are available:

- Java 17
- Node.js 20 or later
- npm 10 or later
- PostgreSQL running on `localhost:5432`
- Kafka running on `localhost:19092`

Current backend defaults:

- application port: `8081`
- database: `cloud_guardrails`
- database user: `lio`
- database password: empty string

These values come from [backend/src/main/resources/application.yaml](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/backend/src/main/resources/application.yaml:1).

## Configuration Model

### Shared Environment Template

A repository-level environment template is provided in [`.env.example`](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/.env.example:1) for documenting expected local variables.

### Frontend Variables

- `VITE_API_BASE_URL`
  Default: `http://localhost:8081`

### Backend Variables

- `GUARDRAILS_ENCRYPTION_SECRET`
  Secret used to encrypt stored cloud credentials.
- `GUARDRAILS_INGESTION_SECRET`
  Shared secret for internal AWS event ingestion.
- `GUARDRAILS_POLLING_ENABLED`
  Enables polling-based ingestion behavior when set to `true`.

## Running the Platform

### Start the backend

```bash
cd backend
./gradlew bootRun
```

Backend base URL:

```text
http://localhost:8081
```

### Start the frontend

Open a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Frontend development URL:

```text
http://localhost:5173
```

## Build and Verification

### Backend

```bash
cd backend
./gradlew test
./gradlew bootRun
```

### Frontend

```bash
cd frontend
npm run lint
npm run build
npm run preview
```

## Operational Capabilities

The platform currently supports:

- tenant-aware authentication and authorization
- AWS account validation prior to onboarding
- cloud event ingestion through internal and application-facing endpoints
- rules retrieval, update, and enablement control
- violation listing, filtering, search, status update, and remediation trigger
- remediation approval, retry, and reverification
- notification retrieval and read-state management
- real-time violation and remediation updates to the UI

## Security and Governance Notes

- JWT is used for API authentication.
- Stored cloud credentials are encrypted using the configured encryption secret.
- The internal AWS ingestion endpoint is protected by a shared secret header.
- Most operational APIs are organization-scoped through the authenticated user context.

This is development-oriented documentation. Before production deployment, the project should also formalize:

- externalized secrets management
- production-grade JWT key rotation
- infrastructure-as-code for runtime dependencies
- CI/CD quality gates
- observability standards for logs, metrics, and tracing

## Module Documentation

Detailed module documentation is available in:

- [backend/README.md](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/backend/README.md:1)
- [frontend/README.md](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/frontend/README.md:1)

## Contribution Guidance

When contributing to this repository:

- commit source code, configuration, migrations, wrapper files, and documentation
- do not commit local environment files
- do not commit generated frontend or backend build output
- keep repository-level documentation aligned with actual runtime behavior

The repository-level [`.gitignore`](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/.gitignore:1) is configured to exclude generated artifacts and local-only files.
