# Cloud Guardrails UI

Frontend for the Cloud Guardrails platform. This app provides:

- authentication and signup
- cloud account onboarding with account validation
- violations and remediations views
- realtime updates over WebSocket/SockJS

The UI is built with React, TypeScript, Vite, Tailwind CSS, Recharts, and STOMP/SockJS.

## Stack

- Node.js 20+ recommended
- npm 10+ recommended
- React 19
- Vite 8
- Tailwind CSS 4

## Project Structure

```text
src/
  api/           API clients and websocket client
  components/    shared UI components
  pages/         route-level screens
  types/         shared frontend types
```

## Prerequisites

This frontend depends on the backend service being available.

Expected backend defaults:

- backend base URL: `http://localhost:8081`
- REST API: `http://localhost:8081`
- WebSocket endpoint: `http://localhost:8081/ws`

The current backend also expects:

- PostgreSQL running on `localhost:5432`
- database name: `cloud_guardrails`
- Kafka running on `localhost:19092`
- Java 17 for the backend

## Backend Setup

If you are running the matching backend project locally, its current defaults are:

- Java 17
- Spring Boot 3.5
- PostgreSQL datasource in `src/main/resources/application.yaml`
- Kafka bootstrap server at `localhost:19092`

Typical backend startup:

```bash
cd /path/to/guardrails
./gradlew bootRun
```

The backend should start on `http://localhost:8081`.

## Frontend Setup

Install dependencies:

```bash
npm install
```

Start the dev server:

```bash
npm run dev
```

Build for production:

```bash
npm run build
```

Run lint:

```bash
npm run lint
```

Preview the production build locally:

```bash
npm run preview
```

## Environment Variables

The frontend supports configuring the API base URL with:

```bash
VITE_API_BASE_URL=http://localhost:8081
```

Create a local env file if needed:

```bash
cp .env.example .env.local
```

If you do not set `VITE_API_BASE_URL`, the app defaults to `http://localhost:8081`.

Suggested `.env.local`:

```bash
VITE_API_BASE_URL=http://localhost:8081
```

## First Run

1. Start PostgreSQL.
2. Start Kafka.
3. Start the backend on port `8081`.
4. Start the frontend with `npm run dev`.
5. Open the Vite URL shown in the terminal, usually `http://localhost:5173`.
6. Sign up for a new organization or log in with an existing user.

## Account Onboarding

The current UI supports AWS account onboarding.

In the Add Account flow, the backend expects:

- `accountId`
- `provider`
- `region`
- `accessKey`
- `secretKey`

There is also a dedicated account validation step before save. The frontend calls:

```text
POST /accounts/validate
```

Successful validation returns:

- `valid`
- `provider`
- `accountId`
- `arn`
- `userId`
- `message`

## Auth Notes

The backend currently returns a raw JWT string from:

- `POST /auth/login`
- `POST /auth/signup`

The frontend stores that token in `localStorage` and uses it for subsequent API calls.

## Main Frontend Features

- login and signup
- dashboard cards and charts
- violations list with search and realtime updates
- remediations list with realtime updates
- cloud account management

## Scripts

```json
{
  "dev": "vite",
  "build": "tsc -b && vite build",
  "lint": "eslint .",
  "preview": "vite preview"
}
```

## Common Issues

### 1. Frontend loads but API calls fail

Check:

- backend is running on `http://localhost:8081`
- `VITE_API_BASE_URL` is correct
- browser console/network tab for 401 or 400 responses

### 2. Signup or login works, but app data is empty

Check:

- backend database is reachable
- the user belongs to an organization
- cloud accounts were created successfully

### 3. Account validation fails

Check:

- AWS account ID matches the supplied credentials
- access key and secret key are valid
- region is valid
- backend can reach AWS STS

### 4. Realtime updates do not appear

Check:

- backend WebSocket endpoint `/ws` is up
- browser is allowed to connect to `http://localhost:8081/ws`
- backend is producing new violations or remediations

### 5. Backend starts but violations/remediations stay empty

The current backend depends on event ingestion and Kafka/AWS polling. Make sure:

- Kafka is running
- events are being produced
- AWS credentials are valid if you are using AWS ingestion

## Current Caveats

- The backend uses JWT claims for tenant/account scoping.
- Depending on backend behavior, newly added cloud accounts may require a fresh login before they affect token-scoped data.
- Production deployment settings are not documented here yet.

## Verification

Current frontend status at the time this README was written:

- `npm run lint` passes
- `npm run build` passes

## Next Improvements

- add a checked-in `.env.example`
- document backend docker/local infra for PostgreSQL and Kafka
- add screenshots or a short local demo flow
