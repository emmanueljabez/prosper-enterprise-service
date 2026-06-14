# AGENTS.md

## Purpose

This repository is the frontend for ProsperMentor. It currently contains the marketplace and session-booking experience, and it is being extended into a corporate mentorship product with employer program management, employee journeys, mentor matching, accountability flows, and analytics.

This file exists to keep frontend extensions aligned with that product direction.

## Tech Stack

- Nuxt 3
- Vue 3
- TypeScript
- Pinia
- Tailwind
- shadcn-nuxt / shared UI primitives

Existing local convention from this repo:

- request flow should follow `component -> store -> http request`

## Product Navigation Model

Use role-specific navigation as the primary UI entry point:

- corporate admin: `navigation/vertical/corporate-admin.ts`
- employee: `navigation/vertical/employee.ts`
- mentor: `navigation/vertical/mentor.ts`

When adding new role-specific product features, update the correct navigation file instead of reusing a generic marketplace link.

## Route Design Rules

### Employer / HR

Company-admin features should live under `/app/admin/*`.

Examples:

- `/app/admin/programs`
- `/app/admin/participants`
- `/app/admin/matches`
- `/app/admin/analytics`
- `/app/admin/trust`

Do not route employer workflows through marketplace-style pages like `/app/mentors/*` unless the feature is genuinely about open mentor discovery.

### Employee

Corporate employee mentorship features should live under `/app/employee/*` where the experience is program-centric.

Examples:

- `/app/employee/programs`
- `/app/employee/journey`
- `/app/employee/matches`
- `/app/employee/mentor`
- `/app/employee/goals`
- `/app/employee/pulses`
- `/app/employee/preferences`

Use existing shared routes like `/app/sessions` and `/app/profile` only where they still make sense.

### Mentor

Mentor-facing workflows should remain under their existing mentor-oriented routes unless a dedicated corporate mentor experience is introduced later.

## Permission And Access Rules

Any new navigation item or route must be wired in all relevant places:

- `types/auth.ts` for permission constants
- `utils/roleManager.ts` for permission definitions and route access rules
- the relevant navigation file for menu visibility
- page-level auth metadata if the page enforces permissions directly

Do not add a navigation item with a permission key that does not exist in the RBAC layer.

## UI Direction For Corporate Mentorship

The corporate product should feel program-centric, not marketplace-centric.

Bias toward:

- `My Program`
- `My Journey`
- `Mentor Matches`
- `Participants`
- `Company Programs`
- `Analytics`
- `Feedback & Pulses`

Avoid defaulting employees back into generic "browse all mentors" flows unless the program explicitly supports that model.

## Frontend Extension Rules

### 1. Keep Role Flows Distinct

- HR/admin views should optimize for program setup, participant management, matching oversight, dashboards, trust, and billing
- employee views should optimize for intake, goals, mentor selection, sessions, journey progress, and feedback
- mentor views should optimize for assigned participants, sessions, availability, and outcome capture

### 2. Add Pages With Nav Changes

If you add or rename a nav item:

- create the page or route target in the same workstream
- or add a deliberate placeholder page
- do not leave dead menu links unless explicitly agreed

### 3. Align Naming With Backend Domain

Prefer the same domain terms across frontend and backend:

- company program
- participant
- mentor match
- journey
- pulse
- trust and safety

Avoid mixing old marketplace labels with new corporate program labels in the same flow.

### 4. Reuse Existing Patterns

- use the repo's Pinia and request-layer patterns
- keep permissions declarative
- avoid one-off fetch logic directly in complex page components when a store already makes sense

### 5. Respect Product Phase Scope

Phase 1 corporate MVP should emphasize:

- company programs
- participants
- mentor matching
- employee journey
- session outcomes
- pulses
- analytics summaries

Advanced surfaces like deep trust tooling, generalized survey builders, or hybrid mentor pools can come later.

## Practical Checklist For New Corporate Pages

When adding a new corporate feature page:

1. add the route under the right role prefix
2. add or confirm the permission key
3. update `utils/roleManager.ts`
4. update the correct role navigation file
5. add page-level permission metadata if needed
6. wire requests through store and request modules
7. make sure the labels match the product language used elsewhere

## Current Bias

When there is a choice, prefer:

- guided journeys over open browsing
- employer program management over generic admin screens
- employee progress visibility over static profile pages
- consistent permissions over temporary route shortcuts

## Feedback And Rating UX

The main feedback product is a WhatsApp-first two-way rating system.

Frontend implications:

- do not assume feedback is primarily collected through web forms
- use web pages to show review status, completion state, score summaries, re-match prompts, and admin reporting
- support a two-way model where mentor and mentee both submit ratings after each session
- design for blind reveal: one party should not see the other party's rating before reveal
- treat the default rating window as 48 hours after session completion
- after 3 shared sessions, support a separate mentorship fit score flow

Avoid spending phase-1 effort on a generic survey-builder style UI unless the backend workflow explicitly requires it.
