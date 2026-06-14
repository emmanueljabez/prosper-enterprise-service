# Corporate Mentorship Platform Product Ideas And Implementation Plan

## Objective

ProsperMentor already supports the core session-booking workflow. The next product step is to turn the platform into a corporate mentorship operating system that helps employers launch structured mentorship programs, match employees to mentors, guide progress over time, measure outcomes, and maintain trust.

This document translates that direction into a concrete implementation plan for the current Spring Boot codebase.

## Product Outcome

The target product is:

> A company can launch a mentorship program using ProsperMentor's existing program catalog and mentor network, enroll employees, match them to mentors, run guided mentorship journeys, track accountability, monitor outcomes in a dashboard, and enforce trust and safety controls.

## Prosper Supply-Led Operating Model

ProsperMentor does not stop being a marketplace and booking platform in the corporate product. The corporate layer should be built on top of Prosper's existing supply:

- Prosper already offers programs as reusable catalog products.
- Prosper already offers mentors as a managed mentor network and booking marketplace.
- company programs should usually start from that existing supply, not from a blank internal-only builder.
- corporate workflows should enhance the existing mentor, program, session, and dashboard features rather than replacing them.

That means the employer product should follow these rules:

- a `company_program` should be anchored to one or more Prosper catalog programs
- HR should be able to compose a company program as an ordered journey of Prosper programs
- HR should be able to use the mentors already linked to those selected Prosper programs as the default mentor pool
- HR can optionally curate a more specific mentor pool from the wider Prosper mentor marketplace when they want a custom cohort design
- existing mentor discovery, mentor profiles, booking, session execution, and dashboards remain first-class surfaces; the corporate layer simply scopes and organizes them

The correct mental model is:

> Prosper supplies the mentors and program catalog. Employers configure company-specific cohorts, audience rules, timelines, and reporting on top of that supply.

## Product Vision And Ideas

ProsperMentor should evolve from a session-booking tool into a corporate mentorship platform that helps employers solve talent problems in a structured way.

The core idea is not:

> employees can book time with mentors

The stronger idea is:

> employers can run measurable mentorship programs that improve employee growth, manager effectiveness, onboarding, retention, and leadership development

### Why Employers Buy This

Employers rarely buy "booking." They buy solutions to business problems such as:

- weak onboarding for new hires
- poor first-time manager readiness
- limited leadership pipeline development
- low engagement among high-potential employees
- weak support for career transitions
- lack of retention and growth support for underrepresented talent groups

### Core Product Positioning

Suggested positioning:

> ProsperMentor helps companies design, run, and measure mentorship programs at scale.

Alternative positioning:

> ProsperMentor is the operating system for corporate mentorship, from program setup to outcomes reporting.

### Key User Personas

#### 1. Employer / HR / L&D Lead

What they need:

- launch programs quickly
- control who participates
- ensure employees get matched well
- monitor engagement and outcomes
- justify budget with measurable results

#### 2. Employee / Mentee

What they need:

- clarity on why they are in the program
- a mentor who fits their goals
- a clear path, not just isolated sessions
- reminders, action items, and progress visibility
- trust that private conversations stay private
- visibility into the wider Prosper program catalog, clearly separated from the company programs they are actively enrolled in

#### 3. Mentor

What they need:

- the right mentees
- context before each session
- structured prompts and journey guidance
- clean scheduling and follow-up workflows
- feedback and quality signals without administrative overhead

### Program Ideas To Support

The platform should support multiple program types, with configurable templates:

- onboarding mentorship
- first-time manager mentorship
- women in leadership
- high-potential talent development
- career transition mentorship
- leadership pipeline development
- return-to-work mentorship
- internal mobility mentorship
- graduate trainee mentorship
- executive sponsorship and mentoring programs

### Experience Ideas By Pillar

#### Program Creation Ideas

- HR launches a program from a prebuilt Prosper catalog program
- HR can compose a company program from one or more Prosper programs when the cohort spans multiple themes or stages
- HR can start from a catalog program, then tailor the cohort audience, timeline, journey, and reporting rules for their company
- HR can design a more custom company program by selecting from the Prosper mentor pool even when the program is not a strict one-to-one copy of a single catalog program
- each template includes goals, participant criteria, suggested journey steps, and success metrics
- companies can brand programs with their own language and objectives
- companies can run multiple cohorts inside the same program type
- programs can be company-wide, department-specific, level-specific, or invite-only

#### Employee Program Discovery Ideas

- employees should be able to browse all Prosper programs, not only the company programs they are already enrolled in
- the employee UI should clearly separate `My Programs` from `All Programs`
- catalog browsing should reuse Prosper's existing program discovery surfaces rather than inventing a second disconnected program directory
- a company-enrolled employee should still be able to see which programs are employer-sponsored versus generally offered by ProsperMentor
- if a program is not employer-sponsored, the UI can route into standard Prosper exploration, mentor discovery, or booking flows instead of pretending it belongs to the company cohort

#### Matching Ideas

- matching should recommend mentors, not just expose a directory
- employees should fill a lightweight intake before matching
- HR should be able to approve or override matches
- in some program modes, employees can choose from the top 3 matched mentors instead of browsing everyone
- each recommendation should explain why the mentor is a fit

#### Guided Journey Ideas

- each program includes a structured path such as 4, 6, or 8 sessions
- each session has a purpose, suggested agenda, and expected output
- milestones could include goal setting, reflection, practice, review, and next-step planning
- the product should support both mentor-led and self-reflection steps between sessions

#### Accountability Ideas

- employees receive reminders before sessions and follow-ups after sessions
- mentors submit a short summary and action items after each session
- mentees confirm what they committed to before the next session
- the system detects inactivity and nudges both sides
- no-shows, repeated cancellations, and stalled journeys should trigger intervention logic

#### Employer Dashboard Ideas

- show participation funnels and completion funnels
- show program-by-program outcomes, not just company-wide totals
- surface themes like confidence growth, promotion readiness, or onboarding support
- compare cohorts and departments over time
- expose mentor quality and responsiveness trends

#### Trust Layer Ideas

- private notes for mentors should remain private unless explicitly marked shareable
- HR should see program health, not intimate coaching details
- employees should be able to report issues confidentially
- the platform should support mentor quality reviews and temporary suspensions
- sensitive analytics should be aggregated or anonymized by default

### What Makes The Product Stronger Than Booking

Booking is infrastructure. The product becomes stronger when it adds:

- structured programs
- explainable matching
- measurable journeys
- accountability mechanisms
- employer analytics
- confidentiality and safety controls

That is the difference between a feature and a platform.

### Future Product Ideas

These are not required for the first rollout, but they are strong expansion directions:

- cohort-based mentorship alongside one-to-one mentorship
- internal mentor pools for companies with their own leaders
- Slack or Microsoft Teams nudges and reminders
- HRIS integrations for employee imports and level/department sync
- AI-assisted agenda generation and session summaries
- AI-assisted mentor recommendations layered on top of deterministic scoring
- pulse surveys before and after programs
- skill assessments and growth benchmarks
- manager visibility modules for approved development goals
- learning content tied to journey milestones
- mentor communities and calibration forums
- badges, certifications, or graduation milestones for completed journeys

### Commercial Ideas

The product can be packaged around program outcomes rather than session counts alone.

Possible commercial structure:

- platform fee per company
- per active participant pricing
- premium analytics tier
- managed program setup and reporting services
- custom mentor network access or premium mentor tiers

### Billing And Metering Forward Path

The commercial model should be reflected in the technical design even if full billing changes are deferred.

Phase 1 should define:

- what counts as a billable participant
- when a participant becomes active for billing purposes
- which analytics are standard versus premium

Recommended technical direction:

- meter usage from `company_program_participants` using explicit status transitions
- define a billable-participant rule such as `accepted_at is not null` and `status in (ACTIVE, PAUSED, COMPLETED)`
- keep premium analytics entitlement checks aligned with existing company subscription concepts already in the codebase
- avoid hardcoding pricing logic into mentorship services; keep billing integration behind subscription or entitlement services

### Strategic Recommendation

The strongest path is to start narrow and win one use case deeply.

Recommended first use case:

- onboarding mentorship for new hires

Why this is strategically strong:

- simple buyer story
- clear participant list
- easy success metrics
- strong corporate ROI narrative
- low ambiguity around journey structure

## Current Platform Baseline

The existing backend already provides several useful primitives:

- `Company` and company-linked employee profiles
- Global `Program` catalog and `ProgramMentor` mappings
- Existing mentor marketplace and mentor profile surfaces
- Session booking, confirmation, cancellation, completion, reminders, and notifications
- Company session views and company dashboard analytics
- Mentor and mentee profiles with goals, interests, ratings, and availability
- Company invitation and whitelist flows

### Relevant Existing Files

- `src/main/java/com/prosper/prospermentor/entity/Company.java`
- `src/main/java/com/prosper/prospermentor/entity/Program.java`
- `src/main/java/com/prosper/prospermentor/entity/ProgramMentor.java`
- `src/main/java/com/prosper/prospermentor/entity/Profile.java`
- `src/main/java/com/prosper/prospermentor/entity/MentorProfile.java`
- `src/main/java/com/prosper/prospermentor/entity/MenteeProfile.java`
- `src/main/java/com/prosper/prospermentor/entity/Session.java`
- `src/main/java/com/prosper/prospermentor/service/ProgramService.java`
- `src/main/java/com/prosper/prospermentor/service/CompanyService.java`
- `src/main/java/com/prosper/prospermentor/service/SessionBookingService.java`
- `src/main/java/com/prosper/prospermentor/service/DashboardService.java`
- `src/main/java/com/prosper/prospermentor/service/ScheduledTaskService.java`
- `src/main/java/com/prosper/prospermentor/service/notification/SessionNotificationService.java`
- `src/main/java/com/prosper/prospermentor/service/notification/CompanyNotificationService.java`
- `src/main/java/com/prosper/prospermentor/controller/ProgramController.java`
- `src/main/java/com/prosper/prospermentor/controller/CompanyController.java`
- `src/main/java/com/prosper/prospermentor/controller/SessionController.java`
- `src/main/resources/db/migration/`

## Core Design Decision

Do not overload the existing `Program` entity with company-specific runtime data.

Use a catalog-plus-runtime model:

- `Program`: reusable Prosper catalog entry such as "Onboarding Mentorship" or "First-Time Manager Growth"
- `ProgramMentor`: existing Prosper mapping that defines the default mentor supply for a catalog program
- `CompanyProgram`: a company-launched instance that wraps one or more Prosper catalog programs with its own audience, timeline, settings, participants, analytics, and outcomes

This keeps the current catalog clean, preserves existing marketplace behavior, and makes the employer workflow explicit.

## Scope

This implementation plan covers six product pillars:

1. Program creation
2. Matching
3. Guided journeys
4. Accountability
5. Employer dashboard
6. Trust layer

## Guiding Principles

- Extend the current architecture instead of rebuilding it
- Extend the current mentor marketplace, booking flows, and dashboards instead of replacing them
- Keep matching rules deterministic and explainable in phase 1
- Make sessions program-aware rather than inventing a parallel session engine
- Preserve confidentiality boundaries between employee, mentor, and employer views
- Track outcomes at participant, mentor, program, and company level
- Roll out in phases, starting with one strong employer use case

## Phase 1 Decisions To Lock Before Building

These decisions should be treated as phase 1 defaults, not open questions:

- Program source: phase 1 company programs should be created from the Prosper `Program` catalog, with one or more selected catalog programs ordered into a company-program journey at creation time.
- Catalog composition: phase 1 should support one or more Prosper programs attached to a company program in a defined order, so the company program behaves like a journey assembled from Prosper offerings.
- Mentor source: phase 1 uses external ProsperMentor mentors. Internal company mentors can be added later as a second mentor-source mode.
- Mentor pool behavior: by default, a company program inherits the mentors already attached to its selected Prosper programs. HR can optionally narrow or expand that pool using the wider Prosper mentor marketplace.
- Employee onboarding: phase 1 supports manual enrollment, whitelist-based invites, and CSV upload. Full HRIS sync is a later integration layer.
- Multi-program participation: allowed. An employee can be active in multiple company programs at once, but each `company_program_participant` row is scoped to exactly one `company_program`.
- Mentor assignment: phase 1 supports one primary mentor per participant per program. Group mentoring and co-mentor models are out of scope for the first release.
- Employee agency: phase 1 supports employee intake, stated preferences, accept/decline of participation, rematch request, and optional selection from the top matched mentors when enabled by the program.
- Employee program discovery: phase 1 employee UX should expose both `My Programs` and an `All Programs` view backed by the existing Prosper `Program` catalog.
- Visibility defaults: employers can see enrollment state, journey progress, attendance, pulse aggregates, and shareable notes. Employers cannot see mentor private notes or confidential issue details unless elevated through an explicit policy workflow.
- Journey flexibility: phase 1 supports mostly linear journeys with optional parallel steps. Full branching is a phase 2 extension and must be modeled so it can be added without breaking data.
- API versioning: keep `/api/v1` for the initial release and restrict v1 changes to additive, backward-compatible changes only.

## Matching Design Reference

Matching is the hardest decision engine in the platform and should not live as a vague subsection inside the main implementation plan.

Create and maintain a dedicated design note:

- `tasks/mentor-matching-design.md`

## Target Domain Model

### Modeling Rules

- Use first-class tables for data that will be queried, filtered, audited, or aggregated.
- Use JSON only for versioned template snapshots, opaque audit payloads, or genuinely variable extensions that are not core reporting dimensions.
- Separate participant lifecycle state from match state and journey state. One status column should not try to carry every workflow concern.

### Runtime Program Tables

#### 1. `company_programs`

Represents a company-launched mentorship program.

Suggested fields:

- `id`
- `company_id`
- `program_id` (anchor Prosper catalog program for fast filtering and compatibility)
- `name`
- `objective`
- `target_audience_description`
- `status` (`DRAFT`, `LIVE`, `PAUSED`, `COMPLETED`, `CANCELLED`, `ARCHIVED`)
- `matching_mode` (`ADMIN_ASSIGN`, `EMPLOYEE_SELECT`, `SYSTEM_ASSIGN`)
- `journey_template_id`
- `visibility_policy_code`
- `max_participants`
- `starts_at`
- `ends_at`
- `created_by`
- `version`
- `created_at`
- `updated_at`

#### 1b. `company_program_catalog_programs`

Stores the Prosper catalog programs attached to a company program as an ordered journey.

Suggested fields:

- `id`
- `company_program_id`
- `program_id`
- `journey_order`
- `journey_stage_name` nullable
- `stage_type` (`CORE`, `OPTIONAL`)
- `created_at`

Notes:

- keep `company_programs.program_id` as the anchor program for fast filtering and backward compatibility
- this table is the real source of truth for the ordered Prosper-program journey inside a company program
- a company program with one attached Prosper program is valid; multi-stage journeys should use additional ordered rows

#### 1c. `company_program_mentor_pool_members`

Stores explicit mentor-pool curation when a company program wants a narrower or broader set than the default Prosper program mappings.

Suggested fields:

- `id`
- `company_program_id`
- `mentor_profile_id`
- `source_type` (`PROGRAM_DEFAULT`, `CURATED_ADD`, `CURATED_EXCLUDE`)
- `created_by`
- `created_at`

Notes:

- default matching should still start from `ProgramMentor` mappings on the selected Prosper programs
- this table exists to support the "design your own cohort from the Prosper mentor pool" workflow without replacing the standard catalog-based path

#### 2. `company_program_metric_targets`

Stores the success metrics that HR wants to track for a program without burying them in JSON.

Suggested fields:

- `id`
- `company_program_id`
- `metric_code`
- `target_value`
- `comparison_operator`
- `created_at`

#### 3. `company_program_participants`

Represents one employee's participation in one company program.

Suggested fields:

- `id`
- `company_program_id`
- `employee_id`
- `status` (`INVITED`, `PENDING_ACCEPTANCE`, `ACTIVE`, `PAUSED`, `WITHDRAWN`, `COMPLETED`)
- `match_status` (`NOT_STARTED`, `RECOMMENDED`, `PENDING_EMPLOYEE_SELECTION`, `ASSIGNED`, `REMATCH_REQUESTED`)
- `status_reason_code`
- `assigned_mentor_id`
- `assigned_by`
- `joined_at`
- `accepted_at`
- `completed_at`
- `last_activity_at`
- `withdrawal_reason`
- `version`
- `created_at`
- `updated_at`

Recommended constraints:

- unique key on `company_program_id, employee_id`
- optional check to ensure one active primary mentor assignment per participant in phase 1

#### 4. `participant_goals`

Goals are core product data and must be queryable.

Suggested fields:

- `id`
- `participant_id`
- `goal_category`
- `goal_text`
- `priority`
- `status` (`PLANNED`, `IN_PROGRESS`, `COMPLETED`, `ABANDONED`)
- `visibility` (`PRIVATE`, `MENTOR_SHARED`, `EMPLOYER_AGGREGATED`)
- `target_date`
- `created_at`
- `updated_at`

#### 5. `participant_preferences`

Stores explicit employee preferences that affect matching and agency.

Suggested fields:

- `participant_id`
- `preferred_language`
- `preferred_timezone`
- `preferred_mentor_gender`
- `preferred_mentor_seniority`
- `allow_employee_selection`
- `wants_same_industry`
- `wants_same_function`
- `notes`
- `updated_at`

#### 6. `participant_intake_responses`

Used only for extensible intake questions that vary by program. Core matching fields should still live in first-class columns or tables.

Suggested fields:

- `id`
- `participant_id`
- `question_code`
- `answer_text`
- `answer_number`
- `answer_boolean`
- `answer_option`
- `created_at`

### Matching Tables

#### 7. `matching_runs`

Stores each recommendation generation attempt for auditability.

Suggested fields:

- `id`
- `participant_id`
- `triggered_by`
- `trigger_type`
- `status`
- `manual_review_required`
- `started_at`
- `completed_at`

#### 8. `mentor_matches`

Stores candidate mentors for a participant.

Suggested fields:

- `id`
- `matching_run_id`
- `participant_id`
- `mentor_id`
- `overall_score`
- `score_confidence`
- `recommendation_reason`
- `status` (`RECOMMENDED`, `SHORTLISTED`, `SELECTED`, `REJECTED`, `EXPIRED`)
- `generated_at`
- `selected_at`

#### 9. `mentor_match_score_components`

Replaces `score_breakdown_json` with queryable components.

Suggested fields:

- `id`
- `mentor_match_id`
- `component_code`
- `raw_value`
- `normalized_value`
- `weight`
- `weighted_score`
- `explanation`

### Journey Tables

#### 10. `journey_templates`

Reusable mentorship journey definitions.

Suggested fields:

- `id`
- `name`
- `program_type`
- `description`
- `default_duration_weeks`
- `template_version`
- `is_active`
- `template_snapshot_json`
- `created_at`

`template_snapshot_json` is acceptable here because it is a versioned authoring artifact, not core runtime reporting data.

#### 11. `journey_steps`

Defines the available steps in a journey template.

Suggested fields:

- `id`
- `journey_template_id`
- `step_key`
- `default_sequence`
- `title`
- `description`
- `step_type` (`SESSION`, `CHECK_IN`, `ACTION_ITEM`, `SURVEY`, `REFLECTION`)
- `required`
- `default_due_offset_days`
- `step_config_json`

#### 12. `journey_step_dependencies`

Allows parallelism and future branching support instead of relying purely on `step_order`.

Suggested fields:

- `id`
- `journey_template_id`
- `from_step_id`
- `to_step_id`
- `dependency_type` (`FINISH_TO_START`, `OPTIONAL_GATE`)

Phase 1 can still render most journeys linearly, but the data model should be dependency-aware from the start.

#### 13. `journey_instances`

Live journey execution for a participant.

Suggested fields:

- `id`
- `participant_id`
- `journey_template_id`
- `status` (`NOT_STARTED`, `IN_PROGRESS`, `PAUSED`, `COMPLETED`, `CANCELLED`)
- `progress_percent`
- `started_at`
- `completed_at`
- `created_at`
- `updated_at`

#### 14. `journey_instance_steps`

Tracks runtime progress for each step.

Suggested fields:

- `id`
- `journey_instance_id`
- `journey_step_id`
- `status` (`PENDING`, `READY`, `COMPLETED`, `SKIPPED`, `BLOCKED`)
- `due_at`
- `completed_at`
- `skipped_reason`
- `blocked_reason`
- `updated_at`

### Session Outcome And Follow-Through Tables

#### 15. `session_outcomes`

Captures the structured outcome of a session.

Suggested fields:

- `id`
- `session_id`
- `participant_id`
- `agenda`
- `summary`
- `shareable_notes`
- `mentor_private_notes`
- `follow_up_required`
- `employee_confidence_score_after`
- `created_by`
- `created_at`
- `updated_at`

#### 16. `session_action_items`

Action items should be tracked individually, not as JSON.

Suggested fields:

- `id`
- `session_outcome_id`
- `owner_type` (`MENTEE`, `MENTOR`)
- `description`
- `status` (`OPEN`, `DONE`, `CANCELLED`)
- `due_at`
- `completed_at`
- `created_at`

#### 17. `session_theme_tags`

Stores controlled thematic tagging for analysis.

Suggested fields:

- `id`
- `session_outcome_id`
- `theme_code`
- `source` (`MENTOR`, `MENTEE`, `SYSTEM`)

### MVP Outcome Collection Table

#### 18. `participant_pulses`

Phase 1 should use a lightweight pulse model instead of a full generalized survey engine.

Suggested fields:

- `id`
- `participant_id`
- `session_id`
- `pulse_type` (`BASELINE`, `MIDPOINT`, `PROGRAM_END`, `D30`, `D60`, `D90`)
- `confidence_score`
- `satisfaction_score`
- `goal_clarity_score`
- `free_text_feedback`
- `status` (`PENDING`, `COMPLETED`, `EXPIRED`)
- `sent_at`
- `completed_at`

This gives the MVP enough structure to measure change over time without building four generic survey tables before the first employer is live.

### Trust, Privacy, And Audit Tables

#### 19. `program_incidents`

Supports trust, safety, and escalation workflows.

Suggested fields:

- `id`
- `company_program_id`
- `participant_id`
- `session_id`
- `reported_by`
- `incident_type`
- `severity`
- `description`
- `status`
- `resolution_notes`
- `created_at`
- `resolved_at`

#### 20. `consent_records`

Stores the participant's explicit consent decisions.

Suggested fields:

- `id`
- `participant_id`
- `consent_type` (`PROGRAM_PARTICIPATION`, `AGGREGATED_ANALYTICS`, `EMPLOYER_PROGRESS_VISIBILITY`)
- `status` (`GRANTED`, `REVOKED`)
- `captured_at`
- `expires_at`

#### 21. `access_audit_logs`

Enterprise trust claims require access auditing.

Suggested fields:

- `id`
- `actor_id`
- `actor_role`
- `resource_type`
- `resource_id`
- `action`
- `reason_code`
- `created_at`

#### 22. `mentor_conflict_checks`

Prevents obvious trust failures before matching.

Suggested fields:

- `id`
- `participant_id`
- `mentor_id`
- `conflict_type`
- `status` (`CLEAR`, `FLAGGED`, `OVERRIDDEN`)
- `notes`
- `checked_at`

### Existing Table Extensions

#### `sessions`

Extend `sessions` to attach bookings to programs and improve operational safety.

Suggested new nullable fields:

- `company_program_id`
- `participant_id`
- `journey_instance_step_id`
- `session_sequence_number`
- `no_show_by`
- `completion_source`
- `version`

#### `mentor_profiles`

Extend `mentor_profiles` with matching and trust metadata:

- `function_area`
- `seniority_level`
- `regions`
- `mentor_capacity`
- `quality_status`
- `background_check_status`
- `last_quality_review_at`

#### `mentee_profiles` and/or `profiles`

Promote matching-relevant fields out of free-form intake wherever possible:

- `department`
- `job_title`
- `employment_level`
- `is_people_manager`
- `preferred_languages`

### JSON Usage Policy

JSON is allowed only in the following cases:

- versioned template snapshots for authored journey templates
- highly variable step configuration that does not drive primary reporting
- append-only audit payloads where relational querying is not needed

JSON should not be used for:

- participant goals
- action items
- match score components
- baseline pulse responses
- core program metrics

## Phase 1 MVP Schema Cut

The target domain model above represents the long-term architecture. Phase 1 does not need every table before the first employer goes live.

### MVP-Blocking Tables

Build these before the first company cohort:

- `company_programs`
- `company_program_participants`
- `participant_goals`
- `participant_preferences`
- `participant_intake_responses`
- `matching_runs`
- `mentor_matches`
- `mentor_match_score_components`
- `journey_templates`
- `journey_steps`
- `journey_step_dependencies`
- `journey_instances`
- `journey_instance_steps`
- `session_outcomes`
- `session_action_items`
- `participant_pulses`
- `consent_records`
- `access_audit_logs`
- `sessions` extensions

### Defer Until After First Live Cohort

These are valuable, but they should not block the first employer rollout:

- `company_program_metric_targets`
- `session_theme_tags`
- `program_incidents`
- `mentor_conflict_checks` if conflict rules can be handled by simpler synchronous checks first
- a generalized survey engine beyond `participant_pulses`

The intent is to validate the core loop first:

1. launch a program
2. enroll and consent participants
3. match mentors
4. run guided sessions
5. capture outcomes and end-of-program pulses
6. show employer adoption and completion reporting

## Application Architecture

### New Services

Create the following services:

#### `CompanyProgramService`

Responsibilities:

- create and manage company programs
- enroll and remove participants
- start or pause program cohorts
- assign journey templates

#### `MatchingService`

Responsibilities:

- calculate mentor recommendations
- expose score breakdowns from `mentor_match_score_components`
- support HR override and manual assignment
- re-run matching when intake or mentor data changes
- enforce conflict and capacity checks at selection time
- follow the dedicated rules in `tasks/mentor-matching-design.md`

#### `JourneyService`

Responsibilities:

- create journey instances from templates
- progress steps when sessions complete
- generate agendas, check-ins, and milestones
- track action items and completion rates
- support dependency-aware step unlocking instead of only linear sequencing

#### `ProgramAnalyticsService`

Responsibilities:

- aggregate company program KPIs
- compute participation, completion, time-to-match, time-to-first-session, satisfaction, and outcomes
- provide program-level dashboard payloads
- rely only on metrics that have explicit collection paths in the data model

#### `TrustSafetyService`

Responsibilities:

- capture confidential reports and incidents
- enforce role-based visibility rules
- track mentor quality signals and remediation
- record consent changes
- run conflict-of-interest checks
- emit audit events for access to sensitive resources

#### `ParticipantPulseService`

Responsibilities:

- create baseline, midpoint, end-program, and follow-up pulses
- collect responses and expose normalized reporting inputs
- keep the MVP outcome-collection model intentionally small

#### `AuditService`

Responsibilities:

- write access audit events for confidential resources
- expose compliance-facing access histories to authorized admins
- support future retention and privacy workflows

### Existing Services To Extend

#### `SessionBookingService`

Add support for:

- booking within a `company_program_id`
- booking within a `participant_id`
- creating a session from a `journey_instance_step_id`
- writing `session_outcomes` after session completion
- handling no-show and follow-up flows
- applying optimistic concurrency checks on mutable session state

#### `DashboardService`

Extend current company analytics with:

- program-level breakdowns
- participant funnel analytics
- journey completion analytics
- mentor quality and satisfaction views
- ROI proxies and cohort comparisons

#### `ScheduledTaskService`

Add background jobs for:

- post-session follow-up prompts
- stale participant nudges
- no-show detection and escalation
- 30/60/90-day outcome pulses
- mentor quality review reminders
- retention-policy jobs for sensitive data

#### Notification Services

Reuse and expand:

- `SessionNotificationService`
- `CompanyNotificationService`

Add templates for:

- program enrollment
- mentor assignment
- pre-session agenda preparation
- post-session summary and action items
- missed session follow-up
- milestone completion
- employer weekly digest

## API Plan

### API Design Rules

- Keep `/api/v1` for the first release.
- All collection endpoints must support pagination via `page` and `size`.
- All list endpoints should support at least `sort` and relevant filters.
- Use additive changes only inside v1.
- Use optimistic concurrency for mutable resources via `version` fields and `If-Match` or explicit version checks.

### Company Program APIs

Suggested endpoints:

- `POST /api/v1/companies/{companyId}/programs`
- `GET /api/v1/companies/{companyId}/programs?status=&page=&size=&sort=`
- `GET /api/v1/company-programs/{companyProgramId}`
- `PATCH /api/v1/company-programs/{companyProgramId}`
- `POST /api/v1/company-programs/{companyProgramId}/launch`
- `POST /api/v1/company-programs/{companyProgramId}/pause`
- `POST /api/v1/company-programs/{companyProgramId}/cancel`
- `POST /api/v1/company-programs/{companyProgramId}/complete`

### Participant APIs

- `POST /api/v1/company-programs/{companyProgramId}/participants`
- `GET /api/v1/company-programs/{companyProgramId}/participants?status=&matchStatus=&search=&page=&size=&sort=`
- `GET /api/v1/participants/{participantId}`
- `POST /api/v1/participants/{participantId}/accept-invite`
- `POST /api/v1/participants/{participantId}/pause`
- `POST /api/v1/participants/{participantId}/withdraw`
- `PUT /api/v1/participants/{participantId}/preferences`
- `PUT /api/v1/participants/{participantId}/goals`
- `PUT /api/v1/participants/{participantId}/intake`

Notes:

- `POST /api/v1/company-programs/{companyProgramId}/participants` creates an invite or direct participant record depending the request mode.
- `POST /api/v1/participants/{participantId}/accept-invite` is the explicit employee acceptance step for invite-driven flows.
- `WITHDRAWN` is a participant choice state, not a failure label.

### Matching APIs

- `POST /api/v1/participants/{participantId}/matching-runs`
- `GET /api/v1/participants/{participantId}/mentor-matches`
- `POST /api/v1/participants/{participantId}/mentor-matches/{mentorMatchId}/select`
- `POST /api/v1/participants/{participantId}/rematch-requests`
- `GET /api/v1/participants/{participantId}/rematch-requests`

Notes:

- Use `matching-runs` instead of an action verb like `re-run`.
- A matching run recalculates recommendations and stores a new recommendation set for auditability.
- Employee-select programs can expose only shortlisted recommendations instead of the full mentor pool.

### Journey APIs

- `POST /api/v1/participants/{participantId}/journey-instances`
- `GET /api/v1/participants/{participantId}/journey-instances/current`
- `GET /api/v1/journey-instances/{journeyInstanceId}/steps`
- `POST /api/v1/journey-instance-steps/{journeyInstanceStepId}/complete`
- `POST /api/v1/journey-instance-steps/{journeyInstanceStepId}/skip`

### Session Outcome APIs

- `POST /api/v1/sessions/{sessionId}/outcomes`
- `GET /api/v1/sessions/{sessionId}/outcomes`
- `POST /api/v1/sessions/{sessionId}/no-show`

### Pulse APIs

- `GET /api/v1/participants/{participantId}/pulses?status=&page=&size=`
- `GET /api/v1/participant-pulses/{pulseId}`
- `POST /api/v1/participant-pulses/{pulseId}/responses`

### Employer Dashboard APIs

- `GET /api/v1/companies/{companyId}/dashboard`
- `GET /api/v1/companies/{companyId}/dashboard/programs?page=&size=&sort=`
- `GET /api/v1/company-programs/{companyProgramId}/dashboard`
- `GET /api/v1/company-programs/{companyProgramId}/analytics?from=&to=&cohort=&department=`

### Trust And Safety APIs

- `POST /api/v1/company-programs/{companyProgramId}/incidents`
- `GET /api/v1/company-programs/{companyProgramId}/incidents?page=&size=&status=&severity=`
- `POST /api/v1/incidents/{incidentId}/resolve`
- `GET /api/v1/mentors/{mentorId}/quality`
- `POST /api/v1/participants/{participantId}/consents`
- `GET /api/v1/participants/{participantId}/consents`

## Frontend Workstream

This document is backend-heavy, but the frontend is on the critical path and should be tracked as a parallel delivery stream.

Phase 1 MVP frontend surfaces:

- HR admin workspace:
  - create and launch company programs
  - invite or upload participants
  - review match recommendations
  - monitor consent, participation, and completion states
  - view dashboard summaries
- employee workspace:
  - accept invitation
  - complete intake, goals, and preferences
  - review shortlisted mentors when enabled
  - view journey progress
  - submit end-of-program pulse responses
- mentor workspace:
  - view assigned participants
  - review participant context and goals
  - capture session outcomes and action items

If the frontend is maintained separately from this Spring Boot repo, its implementation plan should mirror the same phase boundaries and resource names defined here.

## Pillar-By-Pillar Implementation

### 1. Program Creation

Goal:
Allow HR to launch concrete mentorship programs for a company.

Implementation:

- keep `Program` as the global catalog
- create `CompanyProgram` as the company-specific runtime model
- allow HR to set audience, duration, matching mode, confidentiality settings, and success metrics
- support multiple active programs per company
- support recommended master programs as templates for launch

Frontend/admin needs:

- create program from template
- customize description, audience, dates, and mentor selection mode
- view participant list and status
- see consent state and billing-relevant participant state

### 2. Matching

Goal:
Move from open booking to guided mentor assignment.

Implementation:

- create `mentor_matches`
- compute explainable score breakdowns through `mentor_match_score_components`
- store top candidates for auditability
- allow HR override
- support employee-select mode in phase 1 when enabled by the program
- enforce mentor capacity and conflict checks at assignment time, not only recommendation time

Detailed scoring, normalization, fallback behavior, and concurrency handling live in:

- `tasks/mentor-matching-design.md`

### 3. Guided Journeys

Goal:
Make mentorship progress measurable rather than session-by-session only.

Implementation:

- define journey templates for key corporate use cases
- instantiate journeys for each participant
- attach sessions to journey steps
- make each session contribute to milestones, reflections, or action items
- support optional parallel steps through dependency-aware step definitions

Phase 1 limitation:

- phase 1 will behave mostly like a structured linear journey with limited optional parallelism
- full branching logic should be treated as a later extension, but the schema must not assume linear-only sequencing

Initial templates to support:

- onboarding mentorship
- first-time manager development
- leadership growth
- high-potential employee growth
- career transition support

### 4. Accountability

Goal:
Ensure sessions and journeys result in follow-through.

Implementation:

- pre-session agenda prompts
- post-session summary capture
- action item tracking
- automated reminders
- no-show tracking
- stale participant follow-up
- milestone completion prompts

Metrics to capture:

- time to first session
- session completion rate
- no-show rate
- action item completion rate
- participant inactivity rate

### 5. Employer Dashboard

Goal:
Give HR visibility into outcomes, not just bookings.

Implementation:

- extend current company dashboard with program and cohort dimensions
- build participant funnel metrics:
  - invited
  - pending acceptance
  - active
  - completed
  - withdrawn
  - assigned mentor
  - rematch requested
- add quality metrics:
  - average rating
  - feedback coverage
  - mentor responsiveness
  - no-show rate
- add outcome metrics:
  - goal completion
  - confidence change
  - pulse response trends
  - retention and promotion proxies where available

### 6. Trust Layer

Goal:
Make the platform safe and credible for employers and employees.

Implementation:

- define visibility boundaries for mentor notes, employee feedback, and employer summaries
- allow confidential incident reporting
- track mentor quality signals across sessions
- flag repeated cancellations, poor ratings, or complaints
- add mentor review workflows and possible suspension states
- record consent and access audits
- run conflict-of-interest checks before mentor assignment
- define retention and deletion rules for private mentorship data

Policy layer to codify:

- what employers can see
- what remains private between mentor and mentee
- when the platform escalates issues
- what gets anonymized in analytics
- how long sensitive data is retained
- who can access confidential resources and how that access is audited
- how participant data can be exported, deleted, or anonymized when required by policy or law

## Security And Access Control

Add role-aware access rules for:

- company admins
- platform admins
- mentors
- participants

Required controls:

- company admins can only manage their own company programs
- mentors can only see participants assigned to them
- participants can only access their own journey and session outcomes
- confidential reports must never appear in standard employer dashboard payloads

## Mentee Agency Requirements

This product should not treat employees as passive records moving through an HR pipeline.

Phase 1 must include:

- explicit invite acceptance for invite-based programs
- editable goals and preferences before matching
- optional employee selection from shortlisted mentors when the program allows it
- rematch request flow with reason capture
- pause or withdraw actions that are treated as valid participant choices
- session-level and journey-level feedback capture

Mentee agency metrics to track:

- invite acceptance rate
- mentor recommendation acceptance rate
- rematch request rate
- voluntary withdrawal rate
- satisfaction by journey stage

## Program Cancellation And Early Termination Policy

Program cancellation needs explicit behavior, not just a status value.

Phase 1 default behavior:

- a cancelled program moves `company_programs.status` to `CANCELLED`
- active participants move to `PAUSED` with `status_reason_code = PROGRAM_CANCELLED`
- active journey instances move to `PAUSED` or `CANCELLED` based on policy, with phase 1 defaulting to `PAUSED`
- future uncompleted sessions linked to the cancelled program are automatically cancelled by the system unless an admin explicitly preserves them before cancellation
- completed sessions and historical outcomes remain read-only and accessible according to the visibility and retention policy
- cancelled programs remain reportable historically, but no new matching, assignments, or session creation may occur

This policy avoids silent data loss and keeps employer reporting consistent after an early stop.

## Concurrency And Consistency Plan

Enterprise workflows will have concurrent edits and assignment conflicts. The system must handle them intentionally.

Required protections:

- optimistic locking via `version` columns on `company_programs`, `company_program_participants`, and `sessions`
- transactional mentor assignment with capacity validation at commit time
- row-level locking or equivalent safeguards when selecting mentors and creating assignments
- idempotent background jobs for reminders, pulses, and follow-up actions
- unique constraints to prevent duplicate participant enrollment within the same company program
- revalidation of mentor availability when a recommended mentor is actually selected

Specific race conditions to handle:

- two HR admins editing the same program
- two participants selecting a mentor whose remaining capacity is one
- mentor availability changing between recommendation generation and session booking
- repeated webhook or scheduled-job execution on the same resource

## Reporting And Success Metrics

Track these metrics from day one:

- participation rate
- match acceptance rate
- time to first match
- time to first session
- program completion rate
- average session rating
- no-show rate
- action item completion rate
- pulse completion rate
- mentor quality score
- active participants by cohort

Company-facing ROI proxy metrics:

- employee engagement with the program
- manager-development completion
- onboarding completion support
- high-potential participation and progress

## Data Collection Strategy

No metric should appear in the dashboard unless the product has a defined collection path for it.

Phase 1 metric sources:

- participation rate: derived from `company_program_participants`
- match acceptance rate: derived from mentor-match selection and invite-accept flows
- time to first match: derived from participant creation and first selected mentor timestamp
- time to first session: derived from participant activation and first confirmed session timestamp
- program completion rate: derived from participant and journey completion states
- average session rating: derived from session feedback data
- no-show rate: derived from `sessions.no_show_by`
- action item completion rate: derived from `session_action_items`
- pulse completion rate: derived from `participant_pulses`
- mentor quality score: derived from ratings, cancellation history, no-show history, and incident signals

Metrics that require explicit collection mechanics:

- confidence change: collect baseline, midpoint, and end-program self-report pulses
- theme tags: add controlled taxonomy tagging later via `session_theme_tags` once the first cohort validates the reporting need
- journey satisfaction: collect quick pulse responses at key milestones and at program end

Metrics that should not be advertised in MVP dashboards unless the integration exists:

- retention outcomes
- promotion outcomes
- HRIS-derived manager-performance proxies

These can be added later once HRIS integration exists and the data contract is clear.

## Non-Functional Requirements

### Initial Scale Assumptions

Phase 1 should be designed for at least:

- 100 companies
- 25,000 active participants
- 5,000 mentors
- 250,000 sessions
- hourly scheduled jobs over the full active dataset

### Performance Expectations

- company dashboard requests should return in under 2 seconds for standard date ranges
- participant detail and journey views should return in under 1 second
- matching generation should complete in seconds, not minutes, for a single participant

### Multi-Tenancy And Isolation

- every company-scoped query must be filtered by `company_id`
- sensitive resources must be resolved through service-layer authorization, not only controller checks
- database constraints should reinforce tenant-safe relationships wherever possible
- if direct Supabase client access expands later, row-level security should be evaluated explicitly

### Reliability And Recovery

- reminders and pulse jobs must be safe to retry
- confidential and program data must be covered by database backups and restore procedures
- retention-policy jobs must not hard-delete records without an auditable policy decision

### Privacy And Compliance

- define retention schedules by data class: session notes, pulses, incidents, audit logs
- support participant data export and deletion workflows subject to company policy and legal requirements
- record consent evidence and revocation timestamps
- document which analytics are aggregated, anonymized, or individually visible

### Abuse Prevention

- rate-limit write-heavy endpoints such as matching runs, rematch requests, and pulse submissions
- add idempotency handling for external callbacks and scheduled workflows

### Observability

- emit structured logs for matching, assignment, pulse triggers, reminder jobs, and incident workflows
- instrument dashboards, matching latency, and scheduled-task success/failure rates
- keep access audit logs for sensitive resource views

## Delivery Phases

### Phase 0: Foundations

- finalize target use case for first rollout
- lock confidentiality, consent, and retention policies
- define participant intake schema
- design initial matching weights and fallback rules
- agree dashboard KPI definitions
- confirm phase 1 decisions listed in this document

### Phase 1: Company Program Runtime

- create `company_programs` and `company_program_participants`
- create `consent_records` and `access_audit_logs`
- build company program CRUD APIs
- extend sessions with program context
- build employer participant roster views
- capture participant consent before activation
- audit access to sensitive participant and outcome views

Exit criteria:

- a company can launch a program, enroll employees, capture consent, and expose sensitive data through auditable access paths only

### Phase 2: Matching

- create `mentor_matches`
- implement rule-based ranking
- expose top candidates and score explanations
- support HR assignment

Exit criteria:

- each participant can be assigned through a system-assisted matching flow

### Phase 3: Guided Journeys

- create journey templates and instances
- attach sessions to journey steps
- expose employee and mentor journey views

Exit criteria:

- each participant has structured milestones and progress state

### Phase 4: Accountability

- capture agendas, summaries, and action items
- add reminder and follow-up jobs
- add no-show tracking

Exit criteria:

- sessions produce measurable follow-through artifacts

### Phase 5: Employer Analytics

- program-level dashboards
- cohort comparisons
- journey completion analytics
- quality and outcome reporting
- baseline versus end-program pulse reporting

Exit criteria:

- HR can monitor adoption, progress, and quality by program

### Phase 6: Advanced Trust And Safety

- incident reporting
- mentor quality review
- confidentiality-aware reporting
- advanced privacy automation and compliance workflows

Exit criteria:

- the platform supports enterprise-grade controls and escalation

## Recommended MVP

The first MVP should not attempt every program type. Start with one narrow employer problem:

- onboarding mentorship for new hires

Why:

- clear audience
- easy enrollment trigger
- simple matching rules
- measurable journey milestones
- obvious employer ROI narrative

Recommended MVP scope:

- launch onboarding program for a company
- enroll employees manually or from whitelist
- capture employee goals through intake
- recommend mentors with rule-based matching
- allow HR assignment or employee selection from a shortlist
- run a four-step journey
- track session summaries and action items
- support rematch requests and baseline/end-program pulses
- show HR adoption and completion metrics

## Suggested Implementation Sequence In This Repo

1. Finalize `tasks/mentor-matching-design.md` and lock phase 1 product decisions.
2. Add Flyway migrations for `company_programs`, participant tables, matching tables, pulse tables, trust tables, and session extensions.
3. Create JPA entities, repositories, DTOs, and optimistic-lock fields.
4. Add `CompanyProgramService` and `CompanyProgramController`.
5. Add participant intake, preferences, goals, invite-acceptance, and consent flows.
6. Add trust-baseline models and APIs for consent and access audits.
7. Extend `SessionBookingService` and `SessionController` to accept program context.
8. Add `MatchingService` with weighted scoring, component persistence, and transactional assignment checks.
9. Add journey template, dependency, and journey instance models.
10. Add `ParticipantPulseService` and baseline/end-program pulse workflows.
11. Extend `DashboardService` and company dashboard endpoints for program analytics.
12. Expand `ScheduledTaskService` and notifications for accountability workflows.
13. Add advanced trust models such as incidents and richer conflict handling as needed.
14. Add integration tests for the full company-program lifecycle.

## Testing Strategy

### Unit Tests

- matching score calculation
- participant state transitions
- journey progression rules
- dashboard metric aggregation
- confidentiality filtering

### Integration Tests

- company launches program and enrolls employees
- invited employee accepts participation
- participant consent is captured before activation
- participant intake triggers mentor recommendations
- HR assigns mentor or employee selects from a shortlist
- session completion creates outcome and advances journey
- rematch request creates a new matching run without corrupting history
- cancelled program pauses journeys and cancels future sessions predictably
- employer dashboard reflects cohort metrics
- incident reporting stays hidden from standard analytics

## Future Decisions After Phase 1

These do not block the first implementation, but they should be revisited before expanding the product:

- support for internal company mentors and hybrid mentor pools
- HRIS integrations for employee import, org structure, retention, and promotion signals
- generalized survey engine beyond `participant_pulses`
- advanced branching journeys with adaptive step generation
- group mentoring and cohort discussion features
- deeper employer-configurable visibility policies

## Definition Of Success

This initiative is successful when ProsperMentor can support a full employer workflow:

1. a company launches a program
2. employees are enrolled and matched
3. sessions run inside a guided journey
4. follow-through is captured
5. HR can measure participation, quality, and outcomes
6. confidentiality and escalation rules are enforced

At that point, the product has moved beyond booking into a real corporate mentorship platform.
