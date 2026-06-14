# Mentor Matching Design

## Objective

Define a deterministic, explainable, and operationally safe mentor matching model for corporate mentorship programs in ProsperMentor.

This document is the detailed companion to:

- `tasks/corporate-mentorship-platform-implementation-plan.md`

## Design Goals

- produce useful mentor recommendations for each participant
- keep the scoring transparent and debuggable
- handle missing data without producing nonsense
- degrade gracefully when the mentor pool is small
- prevent assignment races and capacity oversubscription
- preserve an audit trail of why a mentor was recommended or selected

## Scope

This design covers:

- candidate generation
- hard filters
- scoring and normalization
- missing-data behavior
- fallback behavior for small or low-quality pools
- recommendation persistence
- assignment-time concurrency checks

This design does not cover:

- AI-based semantic ranking
- internal company-mentor models beyond phase 1 assumptions
- group mentoring

## Matching Modes

The platform supports these phase 1 modes:

- `ADMIN_ASSIGN`: system recommends, HR selects
- `EMPLOYEE_SELECT`: system recommends a shortlist, employee selects
- `SYSTEM_ASSIGN`: system auto-assigns the top valid recommendation subject to policy

## Matching Inputs

### Participant Inputs

- program type
- stated goals
- preferences
- department
- function
- industry
- employment level
- timezone
- language preferences
- people-manager flag

### Mentor Inputs

- specializations
- function area
- industry history
- seniority level
- languages
- timezone
- availability
- capacity
- current quality status
- ratings and reliability history

### Program Inputs

- allowed mentor pool
- matching mode
- conflict policy
- minimum availability requirement
- whether employee choice is enabled

## Candidate Generation

Candidate generation should happen in two stages.

### Stage 1: Pool Selection

Start from the smallest valid mentor pool:

1. mentors explicitly linked to the program template or company program
2. otherwise active external mentors eligible for the program type

### Stage 2: Hard Filtering

Remove mentors who fail non-negotiable constraints:

- mentor not active
- mentor quality status not eligible
- mentor capacity is full
- no qualifying availability in the next scheduling window
- flagged conflict of interest
- language incompatibility when the participant has a hard language requirement

These hard filters are never relaxed.

## Scoring Model

### Score Scale

- each recommendation receives an `overall_score` from `0.0` to `100.0`
- each score is a weighted sum of normalized component scores
- each component score is normalized to `0.0` to `1.0`

### Phase 1 Component Weights

Use the following default weights:

- goal alignment: `25`
- specialization fit: `20`
- function fit: `10`
- industry fit: `10`
- seniority fit: `10`
- language fit: `10`
- timezone fit: `5`
- availability fit: `5`
- mentor quality fit: `5`

Total weight: `100`

These weights should be configurable per program type later, but fixed in phase 1.

### Formula

For each mentor:

`overall_score = 100 * (sum(component_normalized_score * component_weight) / sum(applicable_weights))`

Where:

- `applicable_weights` excludes optional components that cannot be computed because the participant did not provide data
- hard-filter failures do not enter scoring at all

### Score Confidence

Store a `score_confidence` from `0.0` to `1.0`:

`score_confidence = sum(applicable_weights) / 100`

This makes low-information matches visible in the UI and analytics.

## Component Definitions

### 1. Goal Alignment

Range: `0.0` to `1.0`

Method:

- map participant goals to a controlled goal taxonomy
- map mentor specializations and experience to the same taxonomy
- compute overlap ratio

Example:

- full direct overlap: `1.0`
- partial overlap: `0.5`
- no overlap: `0.0`

### 2. Specialization Fit

Range: `0.0` to `1.0`

Method:

- compare participant's desired development themes to mentor specializations
- prioritize exact category overlap before fuzzy category family overlap

### 3. Function Fit

Range: `0.0` to `1.0`

Method:

- exact same function: `1.0`
- adjacent function family: `0.5`
- unrelated: `0.0`

### 4. Industry Fit

Range: `0.0` to `1.0`

Method:

- exact same industry: `1.0`
- adjacent industry cluster: `0.5`
- unrelated or unknown: `0.0`

### 5. Seniority Fit

Range: `0.0` to `1.0`

Method:

- preferred mentor at least one level above participant and within acceptable range: `1.0`
- too junior or excessively senior relative to policy: lower values

Phase 1 rule of thumb:

- same level or below: `0.0`
- one to two levels above: `1.0`
- three or more levels above: `0.6`

### 6. Language Fit

Range: `0.0` to `1.0`

Method:

- full preferred language overlap: `1.0`
- only fallback shared language: `0.5`
- no shared language: hard filter if language is required, otherwise `0.0`

### 7. Timezone Fit

Range: `0.0` to `1.0`

Method:

- within 3 hours: `1.0`
- within 6 hours: `0.6`
- beyond 6 hours: `0.2`

### 8. Availability Fit

Range: `0.0` to `1.0`

Method:

- 3 or more viable slots in the next 14 days: `1.0`
- 1 to 2 viable slots: `0.5`
- no viable slots: hard filter

### 9. Mentor Quality Fit

Range: `0.0` to `1.0`

Method:

- combine mentor rating, completion rate, no-show rate, and incident history into a quality band

Phase 1 simplified mapping:

- high quality: `1.0`
- medium quality: `0.6`
- low but still eligible: `0.3`

## Missing Data Rules

Missing data must not silently behave like a zero score unless the absence itself is meaningful.

Rules:

- if the participant did not provide a preference, exclude that component from `applicable_weights`
- if mentor data is missing for a component that should exist, score that component as `0.0`
- if both sides lack data for a soft component, exclude it from `applicable_weights`

Examples:

- participant gave no industry preference: industry fit is excluded
- mentor has no listed languages: language fit is `0.0` if language matters
- participant gave no function data and mentor has function data: function fit is excluded, not zeroed

## Recommendation Quality Bands

Interpret recommendation quality using explicit bands:

- `75-100`: strong fit
- `60-74.99`: acceptable fit
- `45-59.99`: weak fit
- `<45`: insufficient fit

These bands should affect product behavior:

- strong fit: eligible for default recommendation
- acceptable fit: show with caution
- weak fit: only show when pool is constrained
- insufficient fit: do not auto-assign

## Small-Pool And Low-Score Fallback Behavior

The platform must behave predictably when the mentor pool is thin.

### Fallback Level 1: Strict

- use normal hard filters
- use all scoring components

### Fallback Level 2: Relax Soft Preferences

If fewer than 3 candidates survive:

- relax industry fit from preferred to optional
- relax function adjacency thresholds
- keep conflicts, capacity, activity, and minimum availability as hard filters

### Fallback Level 3: Manual Review Required

If no mentor reaches `60`:

- return recommendations but mark the result `manual_review_required`
- do not allow `SYSTEM_ASSIGN`
- allow HR to override with explicit justification

### Fallback Level 4: No Viable Match

If no candidate survives hard filters:

- return an empty set with explicit failure reasons
- prompt operations to expand mentor pool or adjust the program

## Persistence Model

Store each matching execution as a new run for auditability.

Recommended supporting table:

- `matching_runs`

Suggested fields:

- `id`
- `participant_id`
- `triggered_by`
- `trigger_type` (`INITIAL`, `REMATCH`, `PROFILE_UPDATE`, `MANUAL_REFRESH`)
- `status`
- `started_at`
- `completed_at`
- `manual_review_required`

Each `matching_run` produces zero or more `mentor_matches`.

Each `mentor_match` stores one row per candidate mentor.

Each `mentor_match_score_component` stores one row per scored component.

## Explainability Requirements

Each recommendation should expose:

- overall score
- score confidence
- top 3 positive reasons
- top 1 or 2 constraints or tradeoffs

Example explanation:

- "Strong goal alignment and same function background"
- "Good language match, but timezone overlap is moderate"

## Assignment-Time Concurrency Rules

Recommendations are advisory. Selection must revalidate reality.

On mentor selection:

1. start a transaction
2. lock the participant row
3. lock the mentor row or equivalent capacity state
4. re-check mentor capacity
5. re-check conflict status
6. re-check availability window if the next step is session booking
7. write the selected assignment
8. mark the chosen `mentor_match` as `SELECTED`
9. mark competing recommendations as superseded or expired
10. commit

If capacity changed during selection:

- fail the selection cleanly
- return a conflict response
- trigger a fresh matching run if needed

## API Behavior

### Create Matching Run

`POST /api/v1/participants/{participantId}/matching-runs`

Behavior:

- creates a new matching run
- computes recommendations
- persists candidates and score components

### Get Mentor Matches

`GET /api/v1/participants/{participantId}/mentor-matches`

Behavior:

- returns the latest active recommendation set by default
- may include `manual_review_required`

### Select Mentor Match

`POST /api/v1/participants/{participantId}/mentor-matches/{mentorMatchId}/select`

Behavior:

- transactional selection with capacity revalidation
- returns conflict if the mentor is no longer assignable

### Rematch Request

`POST /api/v1/participants/{participantId}/rematch-requests`

Behavior:

- stores the reason
- triggers a new matching run
- preserves previous assignment history

## Phase 1 Limitations

- no AI embeddings or semantic ranking
- no adaptive per-company weighting
- no internal org-graph aware conflict detection beyond explicit policy checks
- no branch-specific matching rules by journey stage

These can be added later without discarding the audit and scoring model above.

## Test Cases

At minimum, test:

- exact-match high-score candidate
- missing participant industry preference
- missing mentor profile data
- tiny mentor pool with fallback relaxation
- zero viable mentors after hard filters
- two concurrent selections against the same last-capacity mentor
- rematch request creating a new run while preserving history
