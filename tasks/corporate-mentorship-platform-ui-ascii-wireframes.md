# Corporate Mentorship Platform UI ASCII Wireframes

## Purpose

This document translates the implementation plan in [corporate-mentorship-platform-implementation-plan.md](/Users/macbookpro/IdeaProjects/ProsperMentor/tasks/corporate-mentorship-platform-implementation-plan.md) into concrete UI surfaces.

It covers:
- screens already built
- screens partially built and needing expansion
- planned screens required to complete later phases
- the main user journeys for employer admin, employee, mentor, and WhatsApp-based feedback

This is a UI support document, not a replacement for the implementation plan.

## UI Framing Rules

These wireframes assume the corporate product is built on top of ProsperMentor's existing supply and surfaces.

- Prosper already offers mentors, programs, booking, sessions, and dashboards as a service.
- company programs should normally start from Prosper's program catalog and mentor network.
- employers should be able to shape that supply into company-specific cohorts, not rebuild everything from scratch.
- existing mentor profiles, mentor discovery, session booking, session review, and dashboards should be enhanced and scoped, not discarded.
- custom company programs should still feel like "assembled from Prosper offerings" rather than a completely separate LMS product.

## Legend

- `[Built]`: route and core UI already exist
- `[Partial]`: route exists, but deeper workflow/data is still incomplete
- `[Planned]`: required by the plan, not yet implemented
- `Route`: current or proposed frontend route

## Product IA Overview

```text
ProsperMentor Corporate Mentorship Platform
|
+-- Employer / Corporate Admin
|   |
|   +-- Dashboard                        [Built]   /app/admin
|   +-- Company Programs                 [Built]   /app/admin/programs
|   +-- Employees                        [Built]   /app/admin/participants
|   +-- Mentor Matches                   [Built]   /app/admin/matches
|   +-- Session Monitoring               [Built]   /app/admin/sessions
|   +-- Review Analytics                 [Built]   /app/admin/analytics
|   +-- Reports & Operations             [Built]   /app/admin/reports
|   +-- Trust & Safety                   [Built]   /app/admin/trust
|   +-- Billing                          [Partial] /app/admin/billing
|   +-- Settings                         [Partial] /app/admin/settings
|   +-- Journey Template Builder         [Planned] /app/admin/journeys
|   +-- Matching Run Detail              [Planned] /app/admin/matches/[runId]
|   +-- Program Detail Cockpit           [Planned] /app/admin/programs/[id]
|   +-- Incident Workspace               [Planned] /app/admin/trust/incidents/[id]
|
+-- Employee / Mentee
|   |
|   +-- Dashboard                        [Built]   /app/dashboard
|   +-- My Programs                      [Built]   /app/employee/programs
|   +-- All Programs                     [Planned] /app/employee/programs?view=all
|   +-- My Journey                       [Built]   /app/employee/journey
|   +-- Mentor Matches                   [Built]   /app/employee/matches
|   +-- My Mentor                        [Built]   /app/employee/mentor
|   +-- My Sessions                      [Built]   /app/sessions
|   +-- Goals                            [Built]   /app/employee/goals
|   +-- Feedback & Pulses                [Built]   /app/employee/pulses
|   +-- Preferences                      [Built]   /app/employee/preferences
|   +-- Rematch Request                  [Planned] /app/employee/rematch
|   +-- Invite + Consent Acceptance      [Planned] /app/employee/invite/[token]
|
+-- Mentor / Shared Session Ops
|   |
|   +-- Mentor Profile + Booking         [Built]   /app/mentors/[id]
|   +-- Session Request Review           [Built]   /app/sessions/review/[id]
|   +-- Session Outcomes + Action Items  [Built]   /app/sessions/review/[id]
|   +-- Mentor Review History            [Planned] /app/mentor/reviews
|
+-- External Touchpoints
    |
    +-- WhatsApp Session Review Flow     [Built spec / backend live]
    +-- WhatsApp Pulse Flow              [Partial]
    +-- Email / Invite / Reset Password  [Built]
```

## Primary User Journeys

### Employer Admin Journey

```text
Login
  |
  v
Dashboard
  |
  +--> Company Programs --> Create Program --> Attach Journey --> Launch
  |                                  |
  |                                  v
  |                              Employees --> Enroll --> Consent status visible
  |                                  |
  |                                  v
  |                              Mentor Matches --> Assign mentors
  |                                  |
  |                                  v
  |                              Sessions --> Monitor execution
  |                                  |
  |                                  v
  |                              Reports / Analytics --> Export / spot risk
  |                                  |
  |                                  v
  |                              Trust & Safety --> Resolve alerts / audit access
  |
  +--> Billing / Settings
```

### Employee Journey

```text
Login
  |
  v
Dashboard
  |
  +--> My Programs --> Understand active employer program
  |
  +--> All Programs --> Browse the wider Prosper program catalog
  |
  +--> My Journey --> Milestones / next actions / session outcomes
  |
  +--> Mentor Matches --> See assignment status
  |
  +--> My Mentor --> Open mentor profile / book session
  |
  +--> My Sessions --> Track upcoming and past sessions
  |
  +--> Goals --> Follow through on action items
  |
  +--> Feedback & Pulses --> Review status / pulse checkpoints
  |
  +--> Preferences --> Consent / visibility / participation readiness
```

### Mentor / Session Operations Journey

```text
Booking Request Arrives
  |
  v
Session Request Review
  |
  +--> Accept
  |     |
  |     v
  |   Session Happens
  |     |
  |     v
  |   Complete Session
  |     |
  |     +--> Summary
  |     +--> Reflection prompt
  |     +--> Mentor-private notes
  |     +--> Action items
  |     |
  |     v
  |   ReviewWorkflowService opens WhatsApp review cycle
  |
  +--> Decline
        |
        v
      Reason captured
```

## Phase-to-UI Map

```text
Phase 0 Foundations
  - Auth pages
  - nav structure
  - role-aware dashboard shell

Phase 1 Company Program Runtime
  - Company Programs
  - Employees
  - My Programs
  - All Programs
  - Preferences / consent

Phase 2 Matching
  - Mentor Matches (admin)
  - Mentor Matches (employee)
  - My Mentor
  - future match explanation / rematch

Phase 3 Guided Journeys
  - Journey attach in Company Programs
  - My Journey
  - Goals
  - future journey template builder

Phase 4 Accountability
  - Session Monitoring
  - My Sessions
  - Session Review / outcomes
  - action items

Phase 5 Employer Analytics
  - Review Analytics
  - Reports & Operations
  - Feedback & Pulses

Phase 6 Advanced Trust & Safety
  - Trust & Safety
  - Access Audit
  - consent views
  - future incidents / policy workspace
```

## Employer / Corporate Admin Screens

### 1. Corporate Admin Dashboard `[Built]`
Route: `/app/admin`

Purpose:
- executive summary
- switch between overview, employee management, and analytics
- quick visibility into session health, employee onboarding, activity, and risk

```text
+------------------------------------------------------------------------------------------------------------------+
| Top Nav: Dashboard | Company Programs | Employees | Mentor Matches | Mentors | Sessions | Analytics | Billing  |
|          Trust & Safety | Settings                                                                         [User]|
+------------------------------------------------------------------------------------------------------------------+
| Corporate Admin Dashboard                                                                   [Refresh]            |
| Company: Kenya Airways | Period: Last 30 Days                                                                    |
+------------------------------------------------------------------------------------------------------------------+
| Tabs: [Overview] [Employee Management] [Analytics]                                                             |
+------------------------------------------------------------------------------------------------------------------+
| Overview                                                                                                        |
| +--------------------+ +--------------------+ +--------------------+ +--------------------+                    |
| | Registered         | | Participating      | | Sessions (30d)     | | Hours Delivered    |                    |
| | Employees          | | Employees          | |                    | |                    |                    |
| | 5                  | | 4                  | | 12                 | | 17.5               |                    |
| +--------------------+ +--------------------+ +--------------------+ +--------------------+                    |
|                                                                                                                  |
| +--------------------------------------+  +--------------------------------------+                             |
| | Session Health                       |  | Employee Onboarding                  |                             |
| | - completed                          |  | - verified employees                 |                             |
| | - upcoming                           |  | - accepted invites                   |                             |
| | - cancelled                          |  | - awaiting acceptance                |                             |
| +--------------------------------------+  +--------------------------------------+                             |
|                                                                                                                  |
| +--------------------------------------+  +--------------------------------------+                             |
| | Most Active Employees                |  | Recent Activity                      |                             |
| | Ruth Dorcas          4 sessions      |  | Session completed                    |                             |
| | Eric Omondi          3 sessions      |  | Invitation accepted                  |                             |
| | ...                                  |  | Employee registered                  |                             |
| +--------------------------------------+  +--------------------------------------+                             |
+------------------------------------------------------------------------------------------------------------------+
```

Design notes:
- keep as the “portfolio pulse” screen, not detailed workflow management
- row click on recent activity should deep-link into program, employee, or session
- the analytics tab should eventually embed pulse deltas and review completion trends
- retain the existing dashboard patterns and extend them with catalog-program, mentor-supply, and cohort reporting instead of replacing them

### 2. Company Programs Workspace `[Built]`
Route: `/app/admin/programs`

Purpose:
- create and launch company-facing cohorts from Prosper's catalog
- attach journeys
- control lifecycle
- see the program portfolio at a glance

```text
+------------------------------------------------------------------------------------------------------------------+
| Company Programs                                                                 [Refresh] [New Company Program] |
| Launch employer cohorts from Prosper programs and control their lifecycle from one workspace.                  |
+------------------------------------------------------------------------------------------------------------------+
| +----------------+ +----------------+ +----------------+ +----------------+                                     |
| | Total Programs | | Live Programs  | | Draft Programs | | Catalog Linked |                                     |
| | 3              | | 1              | | 1              | | 3 of 3         |                                     |
| +----------------+ +----------------+ +----------------+ +----------------+                                     |
+------------------------------------------------------------------------------------------------------------------+
| Program Workspace                                                                                               |
| Search: [ Search by program name or objective________________________ ]   Status: [All statuses v]             |
+------------------------------------------------------------------------------------------------------------------+
| Company Program     | Prosper Program Journey                 | Mentor Source   | Journey   | Status        |
|---------------------------------------------------------------------------------------------------------------  |
| Journey Smoke Test  | 1. First-Time Manager -> 2. Leadership Basics | Catalog default | Attached  | COMPLETED |
| Validate guided...  |                                             |                 |           |           |
| Actions: [View] [Journey] [Mentor Pool]                                                                       |
|---------------------------------------------------------------------------------------------------------------  |
| Manager Accel DOM   | 1. First-Time Manager -> 2. Feedback Skills | Hybrid curated  | Missing   | LIVE      |
| Help first-time...  |                                             |                 |           |           |
| Actions: [View] [Journey] [Mentor Pool] [Pause] [Complete] [Cancel]                                           |
|---------------------------------------------------------------------------------------------------------------  |
| Leadership Pilot    | 1. Leadership Foundations                  | Catalog default | Missing   | DRAFT     |
| Support managers... |                                             |                 |           |           |
| Actions: [View] [Journey] [Mentor Pool] [Launch] [Cancel]                                                     |
+------------------------------------------------------------------------------------------------------------------+
```

Key states:
- empty portfolio
- draft/live/paused/completed/cancelled
- with attached journey / without journey
- one-stage and multi-stage Prosper-program journeys
- filtered search results
- catalog-default mentor pool vs curated mentor pool

### 3. Create Company Program Dialog `[Built]`
Route context: modal from `/app/admin/programs`

```text
                         +---------------------------------------------------------------------------------------+
                         | Create company program                                                                |
                         | Build an employer cohort from Prosper programs and mentor supply.                     |
                         +---------------------------------------------------------------------------------------+
                         | Launch mode                                                                           |
                         | (o) Build a company journey from Prosper programs                                    |
                         | ( ) Curate a custom cohort from the Prosper mentor pool                              |
                         |                                                                                       |
                         | Company program name                                                                  |
                         | [ Onboarding Mentorship Cohort____________________________________________________ ] |
                         |                                                                                       |
                         | Prosper program journey                                                               |
                         | Stage 1: [ First-Time Manager Growth______________________________________________ v] |
                         | Stage 2: [ Leadership Basics_____________________________________________________ v] |
                         | Stage 3: [ Feedback Skills_______________________________________________________ v] |
                         | [Add another stage] [Remove stage]                                                    |
                         |                                                                                       |
                         | Mentor source                                                                         |
                         | (o) Use mentors attached to selected Prosper programs                                |
                         | ( ) Start from Prosper mentor pool and curate manually                               |
                         | ( ) Hybrid: start with program mentors, then add/remove specific mentors             |
                         |                                                                                       |
                         | Objective                                                                             |
                         | [ What business problem should this program solve?________________________________ ] |
                         |                                                                                       |
                         | Target audience                                                                       |
                         | [ Who should participate in this cohort?__________________________________________ ] |
                         |                                                                                       |
                         | Matching mode            | Guided journey                                             |
                         | [ Admin assign v ]       | [ Attach a guided journey v ]                             |
                         |                                                                                       |
                         | Max participants         | Start date            | End date                           |
                         | [ optional________ ]     | [ yyyy-mm-dd_______ ] | [ yyyy-mm-dd_______ ]             |
                         |                                                                                       |
                         | Right Preview                                                                         |
                         | - Journey stages: 3                                                                   |
                         | - Stage 1: First-Time Manager Growth                                                  |
                         | - Stage 2: Leadership Basics                                                          |
                         | - Stage 3: Feedback Skills                                                            |
                         | - Default mentor pool: 14 mentors                                                     |
                         | - Journey: not attached                                                               |
                         |                                                                                       |
                         |                                                       [Cancel] [Create Company Program]|
                         +---------------------------------------------------------------------------------------+
```

Needed later:
- success-metric target fields
- confidentiality mode
- employer-visible goals / analytics visibility toggles
- preview of linked Prosper mentors before save

### 3b. Prosper Program Picker Drawer `[Planned]`
Route context: drawer from `/app/admin/programs`

```text
                         +------------------------------------------------------------------+
                         | Prosper program catalog                                           |
                         | Choose the Prosper programs that will make up this company journey.|
                         +------------------------------------------------------------------+
                         | Search: [ Search catalog programs______________________________ ] |
                         | Filters: [Leadership v] [Onboarding v] [Manager growth v]        |
                         +------------------------------------------------------------------+
                         | [ ] First-Time Manager Growth                                    |
                         |     12 mentors attached | 6-session journey | Leadership         |
                         |     [Preview mentors] [Preview journey]                           |
                         |------------------------------------------------------------------|
                         | [ ] Leadership Basics                                             |
                         |     8 mentors attached | 4-session journey | Leadership           |
                         |     [Preview mentors] [Preview journey]                           |
                         |------------------------------------------------------------------|
                         | [ ] Onboarding Mentorship                                         |
                         |     16 mentors attached | 5-session journey | Onboarding          |
                         |     [Preview mentors] [Preview journey]                           |
                         +------------------------------------------------------------------+
                         | Selected journey: Stage 1, Stage 2, Stage 3                       |
                         |                                         [Cancel] [Use Selected]   |
                         +------------------------------------------------------------------+
```

### 4. Attach Journey Template Dialog `[Built]`
Route context: modal from `/app/admin/programs`

```text
                         +-------------------------------------------------------------+
                         | Attach journey template                                     |
                         | Add a guided journey to structure this company program.      |
                         +-------------------------------------------------------------+
                         | Program: Manager Acceleration Cohort DOM                     |
                         |                                                             |
                         | Journey template                                             |
                         | [ First-Time Manager Journey v ]                             |
                         |                                                             |
                         | Preview                                                      |
                         | 1. Kickoff and goals                                         |
                         | 2. First 1:1 with mentor                                     |
                         | 3. Reflection / action item follow-through                   |
                         | 4. Midpoint review                                           |
                         | 5. Program-end pulse                                         |
                         |                                                             |
                         |                                      [Cancel] [Attach Journey]|
                         +-------------------------------------------------------------+
```

Future extension:
- show step count, dependency pattern, expected duration
- allow replace journey for `DRAFT` only

### 5. Employees Workspace `[Built]`
Route: `/app/admin/participants`

Purpose:
- choose a company program
- enroll employees
- inspect roster and consent readiness

```text
+------------------------------------------------------------------------------------------------------------------+
| Employees                                                                                      [Refresh] [Enroll]|
| Manage who is attached to each company program.                                                                  |
+------------------------------------------------------------------------------------------------------------------+
| +----------------+ +----------------+ +----------------+                                                         |
| | Programs       | | Employees      | | Roster Size    |                                                         |
| | 3              | | 5 loaded       | | 2 enrolled     |                                                         |
| +----------------+ +----------------+ +----------------+                                                         |
+------------------------------------------------------------------------------------------------------------------+
| Program Context                                                                                                  |
| Company Program: [ Manager Acceleration Cohort DOM_______________________ v ] [Reload programs]                 |
+------------------------------------------------------------------------------------------------------------------+
| Available Employees                                                                                              |
| Search: [ Search employees by name or email________________________ ]                                            |
| [ ] Ruth Dorcas            ruthdorcas339@gmail.com              [select]                                         |
| [ ] Eric Omondi            eric@example.com                     [select]                                         |
| [ ] David Samson           davidsamsonogik@gmail.com            [select]                                         |
+------------------------------------------------------------------------------------------------------------------+
| Current Roster                                                                                                   |
| Search: [ Search enrolled employees____________________________ ]   Status: [All v]                              |
|---------------------------------------------------------------------------------------------------------------  |
| Employee                      | Status     | Consent                         | Enrolled      | Action            |
| Ruth Dorcas                   | ACTIVE     | Program yes / Analytics yes     | Mar 21 2026   | [Remove]          |
| David Samson                  | ENROLLED   | Program pending                 | Mar 21 2026   | [Remove]          |
+------------------------------------------------------------------------------------------------------------------+
```

Future expansion:
- CSV import drawer
- bulk invite panel
- consent drill-down drawer
- rematch request status inline on roster

### 6. Mentor Matches Workspace `[Built]`
Route: `/app/admin/matches`

Purpose:
- select program
- review the Prosper-derived mentor pool
- assign or replace mentor per employee

```text
+------------------------------------------------------------------------------------------------------------------+
| Mentor Matches                                                                                     [Reload All]  |
+------------------------------------------------------------------------------------------------------------------+
| +----------------+ +----------------+ +----------------+ +----------------+                                     |
| | Employees      | | Assigned       | | Pending        | | Mentor Pool    |                                     |
| | 4              | | 2              | | 2              | | 6              |                                     |
| +----------------+ +----------------+ +----------------+ +----------------+                                     |
+------------------------------------------------------------------------------------------------------------------+
| Program Context                                                                                                  |
| Company Program: [ Manager Acceleration Cohort DOM_______________________ v ]                                   |
| Base programs: First-Time Manager Growth, Feedback Skills                                                       |
| Mentor source: Hybrid curated                                                                                   |
| Filter employees: [ Ruth____________________________________ ]                                                    |
+------------------------------------------------------------------------------------------------------------------+
| Mentor Pool                                                                                                      |
| Search: [ Search mentor candidates_______________________________ ]                                              |
| Amos Gachuiri            Leadership / operations / 12 yrs   From base program                                   |
| Jane Mentor              Product / coaching / 8 yrs         Added from Prosper pool                             |
| ...                                                                                                              |
+------------------------------------------------------------------------------------------------------------------+
| Assignment Workspace                                                                                             |
|---------------------------------------------------------------------------------------------------------------  |
| Employee          | Status   | Current Mentor     | Choose Mentor               | Actions                       |
| Ruth Dorcas       | ACTIVE   | Amos Gachuiri      | [ Amos Gachuiri________ v ] | [Save Assignment] [Clear]     |
| David Samson      | ENROLLED | Unassigned         | [ Jane Mentor__________ v ] | [Save Assignment]             |
+------------------------------------------------------------------------------------------------------------------+
```

Phase 2 target addition `[Planned]`:

```text
Right-side drawer: Match rationale
+--------------------------------------------------------------+
| Match Score: 84 / 100                                        |
| Confidence: High                                             |
|--------------------------------------------------------------|
| Goal fit                     28 / 30                         |
| Function fit                 18 / 20                         |
| Seniority gap                12 / 15                         |
| Timezone overlap             10 / 10                         |
| Recent completion history     8 / 10                         |
| Capacity                       8 / 10                         |
|--------------------------------------------------------------|
| Why recommended                                                |
| - Strong first-time manager coaching history                  |
| - Timezone aligns with employee                               |
| - Availability within 7 days                                  |
|--------------------------------------------------------------|
| [Assign] [Compare next candidate] [Override manually]         |
+--------------------------------------------------------------+
```

### 7. Review Analytics `[Built]`
Route: `/app/admin/analytics`

Purpose:
- review-cycle and alert-focused analytics
- visibility into low score signals and do-not-continue risk

```text
+------------------------------------------------------------------------------------------------------------------+
| Review Analytics                                                                                     [Refresh]   |
+------------------------------------------------------------------------------------------------------------------+
| +--------------------+ +--------------------+ +--------------------+ +--------------------+                    |
| | Review Cycles      | | Open Alerts        | | Do Not Continue    | | Low Score Signals  |                    |
| | 18                 | | 3                  | | 1                  | | 4                  |                    |
| +--------------------+ +--------------------+ +--------------------+ +--------------------+                    |
+------------------------------------------------------------------------------------------------------------------+
| Recent Alert Activity                                                                                             |
|---------------------------------------------------------------------------------------------------------------  |
| Signal                  | Program                 | Employee     | Mentor      | Status   | Raised              |
| Low mentor score        | Women in Leadership     | Alice        | Mentor A    | OPEN     | Mar 20 10:30        |
| Do not continue         | Manager Acceleration    | David        | Mentor B    | OPEN     | Mar 20 09:15        |
+------------------------------------------------------------------------------------------------------------------+
```

Planned additions:
- trend chart by week
- response completion funnel
- fit score cohort distribution

### 8. Session Monitoring `[Built]`
Route: `/app/admin/sessions`

Purpose:
- operational oversight of session execution
- filters and session detail drill-down

```text
+------------------------------------------------------------------------------------------------------------------+
| Session Monitoring                                                                                 [Refresh]     |
+------------------------------------------------------------------------------------------------------------------+
| Total | Completed | Upcoming | Cancel / No-show Rate | Avg Rating | Avg Duration | Pending Approvals           |
+------------------------------------------------------------------------------------------------------------------+
| Search: [ Search employee, mentor, department________________ ]                                                 |
| Filters: Department [All v] Mentor [All v] Status [All v] Platform [All v]                                     |
+------------------------------------------------------------------------------------------------------------------+
| All Sessions                                                                                                     |
| Filtered results: 12 of 12                                                                                       |
|---------------------------------------------------------------------------------------------------------------  |
| Employee   | Department | Mentor       | Start        | End          | Status    | Platform | Dur | Rating | > |
| Ruth       | Ops        | Amos         | Mar 22 10:00 | Mar 22 11:00 | COMPLETED | Zoom     | 60  | 4.5    |   |
+------------------------------------------------------------------------------------------------------------------+
```

Session detail modal:

```text
                     +---------------------------------------------------------------+
                     | Session 5c0b...                                               |
                     +---------------------------------------------------------------+
                     | Employee      | Session        | Timing                       |
                     | Ruth Dorcas   | Career growth  | Start / end / duration       |
                     | Mentor        | Program        | Status / rating              |
                     | Amos          | Journey Smoke  | Platform / invoice / notes   |
                     +---------------------------------------------------------------+
```

### 9. Reports & Operations `[Built]`
Route: `/app/admin/reports`

Purpose:
- operational reporting workspace
- exports
- pulse coverage
- portfolio snapshot
- routing from metric to action surface

```text
+------------------------------------------------------------------------------------------------------------------+
| Reports & Operations                                                             [Overview] [Review Analytics]  |
|                                                                                                   [Refresh]     |
+------------------------------------------------------------------------------------------------------------------+
| +--------------------+ +--------------------+ +--------------------+ +--------------------+ +-----------------+ |
| | Program Portfolio  | | Participation      | | Feedback Coverage  | | Pulse Coverage     | | Open Risk      | |
| | 3                  | | 80%                | | 0%                 | | 33%                | | 0              | |
| +--------------------+ +--------------------+ +--------------------+ +--------------------+ +-----------------+ |
+------------------------------------------------------------------------------------------------------------------+
| Exports                                                                                                          |
| [Export Summary CSV] [Export Programs CSV] [Export Pulse CSV] [Export Review Signals CSV]                      |
+------------------------------------------------------------------------------------------------------------------+
| Operational Workstreams                                                                                          |
| [Overview Dashboard] [Review Analytics] [Trust Queue] [Employee Operations]                                     |
+------------------------------------------------------------------------------------------------------------------+
| Pulse Checkpoints                                                                                                |
|---------------------------------------------------------------------------------------------------------------  |
| Program                      | Total | Completed | Pending | Baseline | Program End | Coverage               |
| Journey Smoke Test Program   | 3     | 1         | 2       | 1        | 2           | 33%                    |
+------------------------------------------------------------------------------------------------------------------+
| Program Portfolio Snapshot                                                                                        |
|---------------------------------------------------------------------------------------------------------------  |
| Program                            | Status    | Matching     | Dates         | Capacity                         |
| Journey Smoke Test Program         | Completed | Admin Assign | Flexible      | Open                             |
| Manager Acceleration Cohort DOM    | Live      | Admin Assign | Flexible      | Open                             |
+------------------------------------------------------------------------------------------------------------------+
| Recent Review Signals                                  | Session Operations Snapshot                              |
| No review alerts yet                                   | total sessions / upcoming / cancelled / completion      |
+------------------------------------------------------------------------------------------------------------------+
```

### 10. Trust & Safety `[Built]`
Route: `/app/admin/trust`

Purpose:
- review-alert queue
- rematch and acknowledge actions
- access audit visibility

```text
+------------------------------------------------------------------------------------------------------------------+
| Trust & Safety                                                                 [Review Analytics] [Refresh]      |
+------------------------------------------------------------------------------------------------------------------+
| +----------------+ +----------------+ +----------------+ +----------------+ +----------------+ +---------------+ |
| | Open Alerts    | | High Severity  | | Rematch        | | Audit Events   | | Distinct       | | Consent      | |
| | 0              | | 0              | | Candidates 0   | | (7d) 12        | | Actors 5       | | Updates 2    | |
| +----------------+ +----------------+ +----------------+ +----------------+ +----------------+ +---------------+ |
+------------------------------------------------------------------------------------------------------------------+
| Alert Queue                                                                                                      |
| Filters: Status [All v] Severity [All v] Type [All v]                                                           |
|---------------------------------------------------------------------------------------------------------------  |
| Alert       | Employee      | Mentor      | Program      | Status      | Raised       | Actions                |
| Low fit     | David         | Mentor A    | Cohort A     | OPEN        | Mar 20       | [Acknowledge] [Rematch]|
+------------------------------------------------------------------------------------------------------------------+
| Access Audit                                                                                                     |
|---------------------------------------------------------------------------------------------------------------  |
| Actor                 | Resource              | Target            | Action              | When                  |
| emmanuel@pcash...     | consent_records       | Ruth Dorcas       | PARTICIPANT_CONSENT | Mar 22 09:30         |
| emmanuel@pcash...     | participant_roster    | Journey Program    | VIEW                | Mar 22 09:28         |
+------------------------------------------------------------------------------------------------------------------+
```

Phase 6 target addition `[Planned]`:

```text
Incident Detail Workspace
+---------------------------------------------------------------------------------------------------------------+
| Incident: Low-score repeated mismatch                                                                [Resolve] |
| Program: Manager Acceleration Cohort DOM | Employee: David Samson | Mentor: Mentor B                           |
|---------------------------------------------------------------------------------------------------------------|
| Timeline                                                                                                      |
| - Session 1 review: mentor 2/5                                                                               |
| - Session 2 review: do not continue                                                                          |
| - Fit review: 2/5                                                                                             |
|---------------------------------------------------------------------------------------------------------------|
| Actions                                                                                                       |
| [Recommend Rematch] [Pause Program Access] [Escalate to HR] [Archive Incident]                               |
|---------------------------------------------------------------------------------------------------------------|
| Private notes / policy references / audit trail                                                               |
+---------------------------------------------------------------------------------------------------------------+
```

### 11. Billing `[Partial]`
Routes:
- `/app/admin/billing`
- `/app/admin/billing/subscriptions`
- `/app/admin/billing/invoices`
- `/app/admin/billing/payments`

Target role in plan:
- show active subscription
- participant-based billing counts
- invoice history
- payment status

Suggested wireframe:

```text
+------------------------------------------------------------------------------------------------------------------+
| Billing                                                                                             [Upgrade]    |
+------------------------------------------------------------------------------------------------------------------+
| Active Plan: Corporate Growth                                                                         Status: OK |
| Metered Employees: 24 active this cycle                                                                Renewal...|
+------------------------------------------------------------------------------------------------------------------+
| Tabs: [Overview] [Subscriptions] [Invoices] [Payments]                                                     |
+------------------------------------------------------------------------------------------------------------------+
| Overview                                                                                                        |
| +--------------------+ +--------------------+ +--------------------+                                             |
| | Current Plan       | | Active Employees   | | Next Invoice       |                                             |
| +--------------------+ +--------------------+ +--------------------+                                             |
+------------------------------------------------------------------------------------------------------------------+
| Invoices table / payment failures / billing notes                                                             |
+------------------------------------------------------------------------------------------------------------------+
```

### 12. Settings `[Partial]`
Route: `/app/admin/settings`

Suggested scope:
- company profile
- branding
- notification defaults
- data retention
- review template names / WhatsApp connector settings

```text
+------------------------------------------------------------------------------------------------------------------+
| Settings                                                                                          [Save Changes] |
+------------------------------------------------------------------------------------------------------------------+
| Sections: [Company Profile] [Branding] [Notifications] [Data & Privacy] [Integrations]                         |
+------------------------------------------------------------------------------------------------------------------+
| Company name         [ Kenya Airways________________________ ]                                                  |
| Primary contact      [ emmanuel@pcash.africa________________ ]                                                  |
| Logo                 [Upload]                                                                                    |
| Primary color        [#123456]                                                                                  |
| WhatsApp templates   review_invite / review_reminder / fit_review                                               |
| Retention policy     [ 12 months v ]                                                                            |
+------------------------------------------------------------------------------------------------------------------+
```

## Employee Screens

### 13. Employee Dashboard `[Built]`
Route: `/app/dashboard`

Purpose:
- program-centric employee overview
- empty state if not linked to a company program

```text
+------------------------------------------------------------------------------------------------------------------+
| Your Mentorship Dashboard                                                                         [Refresh]      |
+------------------------------------------------------------------------------------------------------------------+
| +----------------+ +----------------+ +----------------+ +----------------+                                     |
| | Active Programs| | Upcoming       | | Completed      | | Open Actions   |                                     |
| | 0              | | 0              | | 0              | | 0              |                                     |
| +----------------+ +----------------+ +----------------+ +----------------+                                     |
+------------------------------------------------------------------------------------------------------------------+
| No active company programs yet                                                                             |
| You have access to the employee workspace, but no active corporate program has been assigned yet.           |
| [Open My Programs] [Browse All Programs]                                                                    |
+------------------------------------------------------------------------------------------------------------------+
```

When linked to a program, this should evolve into:
- next session
- assigned mentor
- pending feedback/pulses
- next milestone

### 14. My Programs `[Built]`
Route: `/app/employee/programs`

```text
+------------------------------------------------------------------------------------------------------------------+
| Programs                                                                                          [Refresh]     |
+------------------------------------------------------------------------------------------------------------------+
| Tabs: [My Programs] [All Programs]                                                                              |
+------------------------------------------------------------------------------------------------------------------+
| [Card] Journey Smoke Test Program                                                                                |
| Company: Kenya Airways                                                                                           |
| Status: ACTIVE                                                                                                   |
| Objective: Validate guided journeys for new managers                                                             |
| Prosper journey: First-Time Manager Growth -> Leadership Basics                                                  |
| Journey: First-Time Manager Journey                                                                              |
| Mentor: Amos Gachuiri                                                                                             |
| Actions: [Open Journey] [View Mentor] [Open Sessions]                                                            |
+------------------------------------------------------------------------------------------------------------------+
```

Design notes:
- keep employer-enrolled programs and the wider Prosper catalog in the same area, but as clearly separate tabs
- the `My Programs` tab should prioritize progress, mentor, and next action
- the `All Programs` tab should prioritize discovery and clear labels such as `Employer-sponsored` or `Prosper catalog`

### 14b. All Programs `[Planned]`
Route: `/app/employee/programs?view=all`

```text
+------------------------------------------------------------------------------------------------------------------+
| Programs                                                                                          [Refresh]     |
+------------------------------------------------------------------------------------------------------------------+
| Tabs: [My Programs] [All Programs]                                                                              |
+------------------------------------------------------------------------------------------------------------------+
| Search: [ Search all Prosper programs__________________________________ ]  Category: [All v]                   |
+------------------------------------------------------------------------------------------------------------------+
| [Card] First-Time Manager Growth                                                                                |
| Source: Prosper catalog                                                                                          |
| Mentors attached: 12                                                                                             |
| Journey format: 6 sessions                                                                                        |
| Status: Available on Prosper                                                                                     |
| Actions: [View Program] [Explore Mentors]                                                                        |
+------------------------------------------------------------------------------------------------------------------+
| [Card] Leadership Basics                                                                                         |
| Source: Employer-sponsored in Kenya Airways                                                                      |
| Mentors attached: 8                                                                                              |
| Journey format: 4 sessions                                                                                        |
| Status: Enrolled via employer                                                                                    |
| Actions: [Open My Program]                                                                                       |
+------------------------------------------------------------------------------------------------------------------+
| [Card] Career Transition Mentorship                                                                              |
| Source: Prosper catalog                                                                                          |
| Mentors attached: 10                                                                                             |
| Journey format: Flexible                                                                                         |
| Status: Available on Prosper                                                                                     |
| Actions: [View Program] [Explore Mentors]                                                                        |
+------------------------------------------------------------------------------------------------------------------+
```

Behavior notes:
- make it obvious which programs belong to the employee's company versus the general Prosper offering
- reuse existing Prosper program detail and mentor discovery patterns wherever possible
- if the employee is not enrolled through the company, the CTA should route into standard Prosper exploration rather than company-program-specific views

### 15. My Journey `[Built]`
Route: `/app/employee/journey`

```text
+------------------------------------------------------------------------------------------------------------------+
| My Journey                                                                                          [Refresh]    |
+------------------------------------------------------------------------------------------------------------------+
| +----------------+ +----------------+ +----------------+ +----------------+                                     |
| | Programs       | | Completed      | | Milestones     | | Open Action    |                                     |
| | 1              | | Sessions 2     | | Completed 3    | | Items 1        |                                     |
| +----------------+ +----------------+ +----------------+ +----------------+                                     |
+------------------------------------------------------------------------------------------------------------------+
| Program: Journey Smoke Test Program                                                                             |
| Prosper journey: First-Time Manager Growth -> Leadership Basics                                                 |
| Mentor: Amos Gachuiri                                                                                            |
| Next session: Mar 25 11:00                                                                                       |
|---------------------------------------------------------------------------------------------------------------  |
| Milestones                                                                                                       |
| [x] Kickoff session                                                                                              |
| [x] Clarify first-quarter goals                                                                                  |
| [ ] Reflection note due                                                                                          |
| [ ] Midpoint review                                                                                              |
|---------------------------------------------------------------------------------------------------------------  |
| Recent Session Summaries                                                                                          |
| - Clarified team-transition blockers                                                                              |
| - Agreed on first 30-day manager priorities                                                                       |
|---------------------------------------------------------------------------------------------------------------  |
| Open Action Items                                                                                                 |
| [ ] Send mentor draft team charter                                             [Mark complete]                 |
| [ ] Book next session                                                         [Book session]                    |
+------------------------------------------------------------------------------------------------------------------+
```

Phase 3 target addition `[Planned]`:
- explicit step statuses
- dependency-aware rendering
- branch or extra-session recommendations

### 16. Mentor Matches `[Built]`
Route: `/app/employee/matches`

```text
+------------------------------------------------------------------------------------------------------------------+
| Mentor Matches                                                                                                   |
+------------------------------------------------------------------------------------------------------------------+
| +----------------+ +----------------+ +----------------+                                                         |
| | Programs       | | Assigned       | | Pending        |                                                         |
| | 1              | | 1              | | 0              |                                                         |
| +----------------+ +----------------+ +----------------+                                                         |
+------------------------------------------------------------------------------------------------------------------+
| Program: Journey Smoke Test Program                                                                              |
| Company: Kenya Airways                                                                                           |
| Status: Assigned                                                                                                 |
| Mentor: Amos Gachuiri                                                                                            |
| Matching mode: Admin assigned                                                                                    |
| Actions: [Open My Mentor] [Open My Journey]                                                                      |
+------------------------------------------------------------------------------------------------------------------+
```

Future employee-agency extension `[Planned]`:

```text
Need help with this match?
[Request Rematch]
[This mentor is a poor fit]
[Availability conflict]
[Prefer mentor with different background]
[Submit]
```

### 17. My Mentor `[Built]`
Route: `/app/employee/mentor`

```text
+------------------------------------------------------------------------------------------------------------------+
| My Mentor                                                                                                        |
+------------------------------------------------------------------------------------------------------------------+
| Mentor Card                                                                                                      |
| Name: Amos Gachuiri                                                                                              |
| Program: Journey Smoke Test Program                                                                              |
| Expertise: Leadership / operations                                                                               |
| Bio snippet                                                                                                      |
| Actions: [Book Session] [View Full Profile]                                                                      |
+------------------------------------------------------------------------------------------------------------------+
```

### 18. My Sessions `[Built]`
Route: `/app/sessions`

```text
+------------------------------------------------------------------------------------------------------------------+
| My Sessions                                                                                                      |
+------------------------------------------------------------------------------------------------------------------+
| Total | Upcoming | Today | Completed                                                                             |
+------------------------------------------------------------------------------------------------------------------+
| Tabs: [All] [Upcoming] [Today] [Past Sessions]                                                                   |
+------------------------------------------------------------------------------------------------------------------+
| Session Card                                                                                                     |
| Title: First-time manager coaching                                                                               |
| Mentor: Amos Gachuiri                                                                                             |
| Program: Journey Smoke Test Program                                                                              |
| Start: Mar 25 11:00                                                                                              |
| Status: CONFIRMED                                                                                                |
| Actions: [Details]                                                                                               |
+------------------------------------------------------------------------------------------------------------------+
```

### 19. Goals `[Built]`
Route: `/app/employee/goals`

Purpose:
- action-item follow-through view
- bridge between sessions and journey outcomes

```text
+------------------------------------------------------------------------------------------------------------------+
| Goals                                                                                               [Refresh]    |
+------------------------------------------------------------------------------------------------------------------+
| Current Focus Areas                                                                                               |
| - Manage first team transition                                                                                   |
| - Improve 1:1 structure                                                                                          |
| - Build confidence in delegation                                                                                 |
+------------------------------------------------------------------------------------------------------------------+
| Action Plan                                                                                                      |
| [ ] Draft first 30-day team plan                                                                                 |
|     Due: Mar 24 | From session: Manager coaching 01                                                              |
|     [Mark complete]                                                                                               |
| [ ] Prepare reflection for next mentor session                                                                   |
|     Due: Mar 26                                                                                                   |
+------------------------------------------------------------------------------------------------------------------+
| Progress Notes                                                                                                   |
| Last updated from session outcomes and journey action items.                                                     |
+------------------------------------------------------------------------------------------------------------------+
```

### 20. Feedback & Pulses `[Built]`
Route: `/app/employee/pulses`

```text
+------------------------------------------------------------------------------------------------------------------+
| Feedback & Pulses                                                                                   [Refresh]    |
+------------------------------------------------------------------------------------------------------------------+
| +--------------------+ +--------------------+ +--------------------+ +--------------------+                     |
| | Action Required    | | Awaiting Reveal    | | Revealed           | | Delivery Issues    |                     |
| | 0                  | | 0                  | | 0                  | | 0                  |                     |
| +--------------------+ +--------------------+ +--------------------+ +--------------------+                     |
| +--------------------+ +--------------------+ +--------------------+                                             |
| | Pending Pulses     | | Pulse Completion   | | Avg Confidence     |                                             |
| | 2                  | | 33%                | | 4.0                |                                             |
| +--------------------+ +--------------------+ +--------------------+                                             |
+------------------------------------------------------------------------------------------------------------------+
| Pulse Checkpoints                                                                                                 |
|---------------------------------------------------------------------------------------------------------------  |
| Program                    | Checkpoint    | Status     | Scores        | Window                                |
| Journey Smoke Test Program | Baseline      | Completed  | 4 / 5 / 4     | Opened Mar 21, closes Mar 23         |
| Journey Smoke Test Program | Program End   | Pending    | —             | Opens on completion                  |
+------------------------------------------------------------------------------------------------------------------+
| Needs Your Attention                                                                                            |
| No pending reviews                                                                                               |
+------------------------------------------------------------------------------------------------------------------+
| Review History                                                                                                   |
| Session                  | Counterparty    | Status     | My Response    | Window                               |
| Coaching Session 01      | Amos Gachuiri   | Revealed   | Submitted      | Closed                               |
+------------------------------------------------------------------------------------------------------------------+
```

### 21. Preferences `[Built]`
Route: `/app/employee/preferences`

Purpose:
- consent and visibility controls
- participation readiness

```text
+------------------------------------------------------------------------------------------------------------------+
| Preferences                                                                                         [Refresh]    |
+------------------------------------------------------------------------------------------------------------------+
| +--------------------+ +--------------------+ +--------------------+ +--------------------+                     |
| | Programs           | | Assigned Mentors   | | Participation Ready| | Reviews Waiting    |                     |
| | 1                  | | 1                  | | 1                  | | 0                  |                     |
| +--------------------+ +--------------------+ +--------------------+ +--------------------+                     |
+------------------------------------------------------------------------------------------------------------------+
| Matching Setup                                                                                                    |
| Program: Journey Smoke Test Program                                                                              |
| Assignment mode: Admin assign                                                                                    |
| Current mentor: Amos Gachuiri                                                                                    |
+------------------------------------------------------------------------------------------------------------------+
| Consent & Visibility                                                                                              |
|---------------------------------------------------------------------------------------------------------------  |
| Program participation                  [Granted] [Revoke]                                                        |
| Aggregated analytics                   [Granted] [Revoke]                                                        |
| Employer progress visibility           [Granted] [Revoke]                                                        |
+------------------------------------------------------------------------------------------------------------------+
| Your Current Mentor Assignments                                                                                   |
| - Journey Smoke Test Program -> Amos Gachuiri                                                                    |
+------------------------------------------------------------------------------------------------------------------+
| Participation Rules                                                                                               |
| - Booking requires participation consent                                                                         |
| - Reviews are collected via WhatsApp                                                                             |
| - Employer sees aggregated progress, not private comment text                                                    |
+------------------------------------------------------------------------------------------------------------------+
```

Planned addition:
- explicit consent history timeline
- data export / data deletion request CTA

## Mentor / Shared Screens

### 22. Mentor Profile + Booking `[Built]`
Route: `/app/mentors/[id]`

Purpose:
- employee books the assigned mentor in company-program context

```text
+------------------------------------------------------------------------------------------------------------------+
| Mentor Profile                                                                                                   |
+------------------------------------------------------------------------------------------------------------------+
| [Hero] Amos Gachuiri                                                                                             |
| Industry: Professional Mentor                                                                                    |
| Tabs: [Overview] [Reviews] [Achievements]                                                                        |
+------------------------------------------------------------------------------------------------------------------+
| Overview                                                                                                         |
| - Background                                                                                                     |
| - Available Sessions                                                                                              |
| - Full Schedule                                                                                                   |
| [Book a Session]                                                                                                  |
+------------------------------------------------------------------------------------------------------------------+
| Book a Session Dialog                                                                                             |
| Topic:    [ Select topic____________________________ v ]                                                          |
| Platform: [ Select platform_________________________ v ]                                                          |
| Notes:    [ Let the mentor know what you'd like to discuss... ]                                                  |
| Program context: Journey Smoke Test Program                                                                      |
| Employee context: participant_id / company_program_id hidden                                                     |
| [Cancel] [Continue to payment / confirm]                                                                         |
+------------------------------------------------------------------------------------------------------------------+
```

### 23. Session Review / Outcome Capture `[Built]`
Route: `/app/sessions/review/[id]`

Purpose:
- mentor accepts/declines
- then captures outcome data after session completion

```text
+------------------------------------------------------------------------------------------------------------------+
| Review Session Request                                                                                            |
+------------------------------------------------------------------------------------------------------------------+
| Session card                                                                                                     |
| Title: First-time manager coaching                                                                               |
| Mentee: Ruth Dorcas                                                                                              |
| Program: Journey Smoke Test Program                                                                              |
| Requested date / time / notes                                                                                    |
+------------------------------------------------------------------------------------------------------------------+
| Your Response                                                                                                    |
| [Accept] [Decline]                                                                                               |
| If decline:                                                                                                      |
| [ Please provide a reason for declining this session request______________________________ ]                     |
+------------------------------------------------------------------------------------------------------------------+
| Complete Session                                                                                                 |
| Mentor summary                                                                                                   |
| [ Summarize what the mentee achieved or clarified in this session... ]                                           |
| Reflection prompt                                                                                                |
| [ Leave the mentee with a question or reflection... ]                                                            |
| Mentor-only notes                                                                                                |
| [ Optional mentor-only notes... ]                                                                                |
| Action items                                                                                                     |
| - [ Follow up with a concrete next step____________________________ ] [Remove]                                   |
| - [ Add item ]                                                                                                   |
| [Save outcome] [Mark session complete]                                                                           |
+------------------------------------------------------------------------------------------------------------------+
```

## External Touchpoints

### 24. WhatsApp Session Review Flow `[Spec built / backend live]`
Reference: [whatsapp-review-flow-design.md](/Users/macbookpro/IdeaProjects/ProsperMentor/tasks/whatsapp-review-flow-design.md)

This is not a web page, but it is part of the implemented experience and should be treated as first-class UI.

```text
WhatsApp Template
--------------------------------------------------
Hi Emmanuel. Your session with Amos Gachuiri has
been completed.

Please rate the session in a short review.
Your answers remain private until both of you
submit, or the 48-hour review window closes.

[Start]
--------------------------------------------------

WhatsApp Flow Screen 1
--------------------------------------------------
How would you rate the quality of mentor guidance?
( ) Poor
( ) Fair
( ) Good
( ) Very good
( ) Excellent

Did the mentor listen to your context and adapt
the advice to you?
( ) Not at all
( ) Slightly
( ) Somewhat
( ) Mostly
( ) Completely

[Next]
--------------------------------------------------
```

### 25. WhatsApp Pulse Flow `[Partial / planned expansion]`

```text
WhatsApp Pulse Invite
--------------------------------------------------
Hi Ruth. We’d like a quick checkpoint on your
mentorship journey.

This will help ProsperMentor and your employer
understand whether the program is helping.

[Start Pulse]
--------------------------------------------------

Pulse Form
--------------------------------------------------
How confident do you feel in your current role?
( ) 1
( ) 2
( ) 3
( ) 4
( ) 5

How clear are your next growth priorities?
( ) 1
( ) 2
( ) 3
( ) 4
( ) 5

How supported do you feel by this mentorship?
( ) 1
( ) 2
( ) 3
( ) 4
( ) 5

[Submit]
--------------------------------------------------
```

## Planned Screens To Complete Later Phases

### 26. Program Detail Cockpit `[Planned]`
Route: `/app/admin/programs/[id]`

```text
+------------------------------------------------------------------------------------------------------------------+
| Program: Manager Acceleration Cohort DOM                                                [Launch] [Pause] [Edit] |
+------------------------------------------------------------------------------------------------------------------+
| Objective | Audience | Matching Mode | Journey | Dates | Capacity | Completion                                  |
+------------------------------------------------------------------------------------------------------------------+
| Tabs: [Overview] [Employees] [Matches] [Journey] [Sessions] [Pulses] [Trust]                                   |
+------------------------------------------------------------------------------------------------------------------+
| Overview: KPI cards / metric targets / owner / status history                                                   |
+------------------------------------------------------------------------------------------------------------------+
```

Why needed:
- the list page is getting overloaded
- each program needs a dedicated operational cockpit

### 27. Journey Template Builder `[Planned]`
Route: `/app/admin/journeys`

```text
+------------------------------------------------------------------------------------------------------------------+
| Journey Templates                                                                             [New Template]     |
+------------------------------------------------------------------------------------------------------------------+
| Left rail: Templates                                                                                        |
| - First-Time Manager Journey                                                                                |
| - Onboarding Buddy Journey                                                                                  |
| - Women in Leadership Journey                                                                               |
+------------------------------------------------------------------------------------------------------------------+
| Template Canvas                                                                                               |
| [Step 1] Kickoff                     -> [Step 2] First Mentor Session                                         |
|          |                                     |                                                             |
|          +-------------------------------> [Step 3] Reflection Gate                                           |
|                                                |                                                             |
|                                                +--> [Step 4] Midpoint Review                                  |
|                                                +--> [Step 5] Program-End Pulse                                |
+------------------------------------------------------------------------------------------------------------------+
| Step Detail Drawer                                                                                             |
| Step name / type / owner / due rule / dependency / completion criteria                                         |
+------------------------------------------------------------------------------------------------------------------+
```

### 28. Rematch Request Workspace `[Planned]`
Routes:
- employee: `/app/employee/rematch`
- admin handling inside `/app/admin/trust` or `/app/admin/matches`

```text
Employee Rematch Request
+---------------------------------------------------------------------------------------------------------------+
| Request a rematch                                                                                             |
| Program: Manager Acceleration Cohort DOM                                                                      |
| Current mentor: Mentor B                                                                                      |
|---------------------------------------------------------------------------------------------------------------|
| Reason                                                                                                        |
| ( ) Availability mismatch                                                                                     |
| ( ) Goals do not align                                                                                        |
| ( ) Communication / fit issue                                                                                 |
| ( ) Prefer different background                                                                               |
| ( ) Other                                                                                                     |
| [ Comment______________________________________________________________ ]                                     |
| [Cancel] [Submit Request]                                                                                     |
+---------------------------------------------------------------------------------------------------------------+
```

### 29. Consent-First Invite Acceptance `[Planned]`
Route: `/app/employee/invite/[token]`

```text
+------------------------------------------------------------------------------------------------------------------+
| Welcome to Kenya Airways Mentorship Program                                                                     |
+------------------------------------------------------------------------------------------------------------------+
| Program: Manager Acceleration Cohort DOM                                                                        |
| Objective: Help first-time managers build confidence and execution discipline                                   |
| Mentor assignment: after enrollment                                                                             |
|---------------------------------------------------------------------------------------------------------------  |
| Consent checklist                                                                                               |
| [ ] I consent to participate in this company mentorship program                                                 |
| [ ] I consent to aggregated analytics                                                                           |
| [ ] I understand employer progress visibility rules                                                             |
|---------------------------------------------------------------------------------------------------------------  |
| [Decline] [Accept and Join Program]                                                                             |
+------------------------------------------------------------------------------------------------------------------+
```

## UI Design Rules For This Product

These rules should stay consistent across all future screens:

- admin screens should feel like operations software, not a mentor marketplace
- employee screens should be calm and guidance-oriented, not overloaded with admin language
- the main unit is the `company program`, not the global mentor catalog
- trust data should never expose raw private review comments to employer admins
- action surfaces should sit next to the metric that motivates the action
- empty states should explain whether the issue is:
  - no data yet
  - not enrolled yet
  - waiting on consent
  - waiting on WhatsApp response
- consent, review, pulse, and rematch states should be visible inline wherever they block progress

## Recommended Build Order For UI

```text
1. Program Detail Cockpit
2. Matching Run Detail + rationale drawer
3. Rematch request flow
4. Journey Template Builder
5. Consent-first invite acceptance
6. Incident detail workspace
7. Billing metering views
```

## Notes

- This document intentionally includes both current screens and future target screens.
- WhatsApp review and pulse flows are part of the product UI, even though they live outside the main web app.
- The current local frontend already supports the built admin reports workspace and pulse checkpoint views.
- The public deployed frontend still needs a frontend deployment to expose some of the newer admin reporting surfaces.
