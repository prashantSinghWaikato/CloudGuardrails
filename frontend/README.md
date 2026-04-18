# CloudGuardrails Frontend

The frontend is the operator console for CloudGuardrails. It provides the user-facing experience for authentication, cloud account onboarding, security posture visibility, remediation operations, and real-time monitoring.

## Purpose

This application translates backend guardrail workflows into a usable operational interface for security and platform teams. It focuses on fast visibility, clear navigation, and live updates for violations and remediation activity.

## User Capabilities

- sign up and log in to the platform
- access protected application routes
- onboard and validate AWS cloud accounts
- review dashboard summaries and charts
- inspect violations and remediation status
- monitor rule posture and notification-driven updates

## Technical Stack

- React 19
- TypeScript
- Vite
- Tailwind CSS 4
- React Router
- Recharts
- SockJS / STOMP client

## Application Structure

```text
src/
├── api/         HTTP and WebSocket client utilities
├── assets/      Images and static UI assets
├── components/  Shared layout, tables, charts, and modals
├── mock/        Local mock data helpers
├── pages/       Route-level application screens
├── types/       Shared TypeScript models
├── App.tsx      Route composition and authenticated layout
└── main.tsx     Application bootstrap
```

## Runtime Dependencies

Recommended local versions:

- Node.js 20 or later
- npm 10 or later

Expected backend dependency:

- CloudGuardrails backend running on `http://localhost:8081`

## Configuration

Frontend runtime configuration uses Vite environment variables.

### Supported Variable

- `VITE_API_BASE_URL`
  Default: `http://localhost:8081`

Environment template:

- [`.env.example`](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/frontend/.env.example:1)

Typical local setup:

```bash
cp .env.example .env.local
```

```bash
VITE_API_BASE_URL=http://localhost:8081
```

## Running Locally

Install dependencies:

```bash
npm install
```

Start the development server:

```bash
npm run dev
```

Build for production:

```bash
npm run build
```

Run linting:

```bash
npm run lint
```

Preview the production build locally:

```bash
npm run preview
```

Default development URL:

```text
http://localhost:5173
```

## Route Model

The frontend currently exposes:

- `/` for login
- `/signup` for registration
- `/dashboard` for operational overview
- `/violations` for findings management
- `/remediations` for remediation workflow tracking
- `/accounts` for cloud account onboarding
- `/rules` for rule visibility and control

Protected routes are enforced through the authenticated layout in [src/App.tsx](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/frontend/src/App.tsx:1).

## Integration Model

The application integrates with the backend through:

- REST APIs for authentication, accounts, violations, remediations, rules, and notifications
- WebSocket/STOMP for live violation and remediation updates

The frontend currently expects:

- REST base URL at `http://localhost:8081`
- WebSocket endpoint at `http://localhost:8081/ws`

## Main Interface Areas

### Authentication

- login
- signup
- token persistence in local storage
- protected route access

### Dashboard

- summary cards
- severity and compliance visualizations
- top-rule trend insight
- recent violations table

### Cloud Accounts

- account creation
- account validation before persistence
- account listing and management

### Violations and Remediations

- live updates
- table-based review workflows
- remediation state visibility
- drill-in workflow support

### Rules

- rule retrieval
- operational visibility into configured controls

## Local Development Notes

- ensure the backend is running before loading the UI
- if API calls fail, verify `VITE_API_BASE_URL`
- if live updates fail, confirm the backend WebSocket endpoint is available
- if authentication works but operational data is empty, verify backend database and ingestion state

## Quality Commands

Available scripts from [package.json](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/frontend/package.json:1):

```json
{
  "dev": "vite",
  "build": "tsc -b && vite build",
  "lint": "eslint .",
  "preview": "vite preview"
}
```

## Related Assets

- application source: [src](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/frontend/src:1)
- public assets: [public](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/frontend/public:1)
- Vite config: [vite.config.ts](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/frontend/vite.config.ts:1)
- package manifest: [package.json](/Users/lio/Documents/Waikato_2nd_sem/CloudGuardrails/frontend/package.json:1)
