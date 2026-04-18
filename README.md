# CloudGuardrails

CloudGuardrails is a full-stack cloud security platform for detecting policy violations, tracking remediation workflows, and giving teams a single place to monitor cloud activity. This repository contains both the Spring Boot backend and the React frontend in one repo.

## Repository Layout

```text
CloudGuardrails/
├── backend/   Spring Boot API, rule engine, persistence, auth, ingestion
├── frontend/  React + Vite dashboard for auth, accounts, violations, remediations
├── .env.example
├── .editorconfig
└── README.md
```

## Stack

### Backend

- Java 17
- Spring Boot 3.5
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL
- Kafka
- AWS SDK v2
- WebSocket/STOMP

### Frontend

- Node.js 20+
- npm 10+
- React 19
- TypeScript
- Vite
- Tailwind CSS 4
- Recharts

## Prerequisites

Install or run these locally before starting the full application:

- Java 17
- Node.js 20 or newer
- npm 10 or newer
- PostgreSQL running on `localhost:5432`
- Kafka running on `localhost:19092`

Current backend defaults from `backend/src/main/resources/application.yaml`:

- database: `cloud_guardrails`
- database user: `lio`
- database password: empty string
- API port: `8081`

If your local setup differs, update the backend config before running.

## Quick Start

### 1. Clone and configure

```bash
git clone <your-repo-url>
cd CloudGuardrails
cp .env.example .env
```

The root `.env` file is a convenient place to keep the shared values used during local development. The frontend and backend still use their own runtime conventions, so treat the root file as project documentation unless you wire it into your startup workflow.

### 2. Start the backend

```bash
cd backend
./gradlew bootRun
```

The backend starts on `http://localhost:8081`.

Useful backend commands:

```bash
./gradlew test
./gradlew bootRun
```

### 3. Start the frontend

Open a second terminal:

```bash
cd frontend
npm install
npm run dev
```

The Vite development server usually starts on `http://localhost:5173`.

Useful frontend commands:

```bash
npm run dev
npm run build
npm run lint
npm run preview
```

## Environment Variables

### Frontend

Frontend configuration is handled with Vite environment variables.

- `VITE_API_BASE_URL`
  Default: `http://localhost:8081`

The frontend already includes `frontend/.env.example` with this value.

### Backend

The backend currently uses these environment variables:

- `GUARDRAILS_ENCRYPTION_SECRET`
  Used to encrypt stored cloud credentials.
- `GUARDRAILS_INGESTION_SECRET`
  Shared secret for ingestion-related flows.
- `GUARDRAILS_POLLING_ENABLED`
  Enables polling-based ingestion when set to `true`.

The backend also reads database and Kafka settings from `backend/src/main/resources/application.yaml`.

## Main Features

- JWT-based signup and login
- Organization-aware account management
- AWS account validation before onboarding
- Cloud event ingestion and rule evaluation
- Violation tracking and remediation workflows
- Realtime updates over WebSocket/STOMP
- Frontend dashboards for violations and remediations

## Development Workflow

### Backend

The backend module contains:

- authentication and authorization
- cloud account onboarding
- rule evaluation
- event ingestion
- remediation orchestration
- WebSocket updates

See `backend/README.md` for backend-specific details.

### Frontend

The frontend module contains:

- login and signup flows
- protected routes
- cloud account onboarding UI
- violations and remediations pages
- charts and live updates

See `frontend/README.md` for frontend-specific details.

## Recommended First Commit Hygiene

Before pushing this repository, make sure generated artifacts stay untracked:

- `frontend/node_modules/`
- `frontend/dist/`
- `backend/build/`
- `backend/.gradle/`
- IDE files such as `.idea/`

This root `.gitignore` now covers those paths.

## Notes

- The backend currently assumes local PostgreSQL and Kafka unless you change its configuration.
- The repo already contains module-level `README.md` files for more detailed frontend and backend setup.
- If you plan to onboard other developers, the next useful additions would be Docker Compose for PostgreSQL and Kafka, plus CI for frontend lint/build and backend tests.
