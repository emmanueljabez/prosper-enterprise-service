# WhatsApp Review Flow Design

## Purpose

This document defines the WhatsApp-first survey and review flows for ProsperMentor.

The goal is not to build a generic survey engine. The goal is to run a structured, two-way review system through WhatsApp after mentorship sessions and after the third shared session in a mentor relationship.

This design assumes:

- all review collection happens through WhatsApp
- the backend owns state, scoring, blind reveal, reminders, expiry, and aggregation
- the frontend mainly shows status, summaries, and admin actions

## Product Rules

- Every completed session opens a two-way review cycle.
- The mentor rates the mentee.
- The mentee rates the mentor.
- Reviews are blind until both parties submit or the review window closes.
- The review window is 48 hours from session completion.
- After 3 completed sessions with the same mentor assignment, both parties also submit a fit review.
- Low scores trigger Prosper ops review and possible rematch workflows.

## Recommended Delivery Model

Recommended product shape:

- treat each survey as one logical form with fixed questions
- use a multi-screen native WhatsApp Flow form as the primary submission model
- open the form from the approved template's `Start` button
- submit the review once at the end of the flow as a single payload

The ASCII diagrams below show the intended WhatsApp Flow form layout. The backend should treat each completed form as one submission event, not as a series of chat replies.

## Review Types

### 1. Session Review: Mentee Rates Mentor

Trigger:

- created immediately when a session is marked `COMPLETED`

Questions:

- `quality_of_guidance`
- `listened_and_adapted`
- `presence_and_punctuality`
- `knowledge_generosity`
- `space_for_my_insights`
- `recommend_continue`
- `optional_comment`

### 2. Session Review: Mentor Rates Mentee

Trigger:

- created immediately when a session is marked `COMPLETED`

Questions:

- `preparedness`
- `active_engagement`
- `respect_for_time`
- `ownership_mindset`
- `reciprocal_value`
- `recommend_continue`
- `optional_comment`

### 3. Fit Review: Both Sides Rate The Match

Trigger:

- created after the 3rd completed session on the same mentor assignment

Questions:

- `fit_score`
- `want_to_continue_with_same_match`
- `optional_comment`

## Interaction Rules

These rules apply to all review flows.

- Each review request is opened from a WhatsApp Flow `Start` button.
- The user completes one form across multiple flow screens.
- ProsperMentor stores the review when the full form is submitted.
- If the user abandons the form before submission, the request stays pending.
- Reminder messages reopen the same form until the request is submitted or expires.

Validation behavior:

- required scored fields cannot be blank
- optional comment can be left empty
- backend validates the submitted payload against the expected fields and score ranges
- if a review is expired or already submitted, a reopened flow should show a closed-state message instead of accepting a second submission

## Survey Form Structure

## A. Mentee Rates Mentor

### Form Summary

- audience: employee / mentee
- target: assigned mentor
- answer scale: 1 to 5
- scoring dimensions: 5
- recommendation question: yes or no
- final comment: optional

### Preferred WhatsApp Flow Wireframe

```text
+------------------------------------------------------+
| ProsperMentor                                        |
| Rate Your Mentor                                     |
|------------------------------------------------------|
| Session with: {{mentor_name}}                        |
| Your answers stay private until reveal.              |
| Review window: 48 hours                              |
|                                                      |
|                          [ Start Review ]            |
+------------------------------------------------------+
                         |
                         v
+------------------------------------------------------+
| Step 1 of 3                                          |
|------------------------------------------------------|
| Quality of guidance                                  |
| ( ) 1   ( ) 2   ( ) 3   ( ) 4   ( ) 5                |
|                                                      |
| Listened and adapted                                 |
| ( ) 1   ( ) 2   ( ) 3   ( ) 4   ( ) 5                |
|                                                      |
|                                      [ Next ]        |
+------------------------------------------------------+
                         |
                         v
+------------------------------------------------------+
| Step 2 of 3                                          |
|------------------------------------------------------|
| Presence and punctuality                             |
| ( ) 1   ( ) 2   ( ) 3   ( ) 4   ( ) 5                |
|                                                      |
| Knowledge generosity                                 |
| ( ) 1   ( ) 2   ( ) 3   ( ) 4   ( ) 5                |
|                                                      |
| Space for my insights                                |
| ( ) 1   ( ) 2   ( ) 3   ( ) 4   ( ) 5                |
|                                                      |
|                           [ Back ]   [ Next ]        |
+------------------------------------------------------+
                         |
                         v
+------------------------------------------------------+
| Step 3 of 3                                          |
|------------------------------------------------------|
| Continue with {{mentor_name}}?                       |
| ( ) Yes                                ( ) No        |
|                                                      |
| Optional comment                                     |
| [_______________________________________________]    |
|                                                      |
|                         [ Submit Review ]            |
+------------------------------------------------------+
```

### Intro Message

```text
Hi {{mentee_first_name}}. Your session with {{mentor_name}} has been completed.

Please rate the session in a short review. Your answers remain private until both of you submit, or the 48-hour review window closes.

Click Start to begin.
```

Preferred quick reply button:

- `Start review`

### Question 1

Code: `quality_of_guidance`

```text
1/7. How would you rate the quality of guidance from {{mentor_name}}?

Options:
1 = Poor
2 = Fair
3 = Good
4 = Very good
5 = Excellent
```

### Question 2

Code: `listened_and_adapted`

```text
2/7. Did {{mentor_name}} listen to your context and adapt the advice to you?

Options:
1 = Not at all
2 = Slightly
3 = Somewhat
4 = Mostly
5 = Completely
```

### Question 3

Code: `presence_and_punctuality`

```text
3/7. How would you rate {{mentor_name}} on punctuality and presence during the session?

Options:
1 = Late and distracted
2 = Inconsistent
3 = Acceptable
4 = Punctual and focused
5 = Fully present and on time
```

### Question 4

Code: `knowledge_generosity`

```text
4/7. How would you rate the value of the resources, examples, or insights shared by {{mentor_name}}?

Options:
1 = Not valuable
2 = Slightly valuable
3 = Moderately valuable
4 = Very valuable
5 = Extremely valuable
```

### Question 5

Code: `space_for_my_insights`

```text
5/7. Did {{mentor_name}} make space for your own ideas and perspective?

Options:
1 = Not at all
2 = A little
3 = Somewhat
4 = Quite a bit
5 = Definitely
```

### Question 6

Code: `recommend_continue`

```text
6/7. Would you be happy to continue mentorship with {{mentor_name}}?

Options:
1 = Yes
2 = No
```

### Question 7

Code: `optional_comment`

```text
7/7. Optional: share one short comment on what helped most or what could improve.

Optional field: add a short comment or leave it blank.
```

### Completion Message

```text
Thanks. Your review has been submitted.

We will reveal the review once both sides submit, or when the 48-hour review window closes.
```

## B. Mentor Rates Mentee

### Form Summary

- audience: mentor
- target: employee / mentee
- answer scale: 1 to 5
- scoring dimensions: 5
- recommendation question: yes or no
- final comment: optional

### Preferred WhatsApp Flow Wireframe

```text
+------------------------------------------------------+
| ProsperMentor                                        |
| Rate Your Mentee                                     |
|------------------------------------------------------|
| Session with: {{mentee_name}}                        |
| Your answers stay private until reveal.              |
| Review window: 48 hours                              |
|                                                      |
|                          [ Start Review ]            |
+------------------------------------------------------+
                         |
                         v
+------------------------------------------------------+
| Step 1 of 3                                          |
|------------------------------------------------------|
| Preparedness                                         |
| ( ) 1   ( ) 2   ( ) 3   ( ) 4   ( ) 5                |
|                                                      |
| Active engagement                                    |
| ( ) 1   ( ) 2   ( ) 3   ( ) 4   ( ) 5                |
|                                                      |
|                                      [ Next ]        |
+------------------------------------------------------+
                         |
                         v
+------------------------------------------------------+
| Step 2 of 3                                          |
|------------------------------------------------------|
| Respect for time                                     |
| ( ) 1   ( ) 2   ( ) 3   ( ) 4   ( ) 5                |
|                                                      |
| Ownership mindset                                    |
| ( ) 1   ( ) 2   ( ) 3   ( ) 4   ( ) 5                |
|                                                      |
| Reciprocal value                                     |
| ( ) 1   ( ) 2   ( ) 3   ( ) 4   ( ) 5                |
|                                                      |
|                           [ Back ]   [ Next ]        |
+------------------------------------------------------+
                         |
                         v
+------------------------------------------------------+
| Step 3 of 3                                          |
|------------------------------------------------------|
| Continue with {{mentee_name}}?                       |
| ( ) Yes                                ( ) No        |
|                                                      |
| Optional comment                                     |
| [_______________________________________________]    |
|                                                      |
|                         [ Submit Review ]            |
+------------------------------------------------------+
```

### Intro Message

```text
Hi {{mentor_first_name}}. Your session with {{mentee_name}} has been completed.

Please rate the session. Your answers remain private until both of you submit, or the 48-hour review window closes.

Click Start to begin.
```

Preferred quick reply button:

- `Start review`

### Question 1

Code: `preparedness`

```text
1/7. How prepared was {{mentee_name}} for the session?

Consider clarity of goals, context shared, and use of time.

Options:
1 = Not prepared
2 = Slightly prepared
3 = Moderately prepared
4 = Well prepared
5 = Extremely prepared
```

### Question 2

Code: `active_engagement`

```text
2/7. How would you rate {{mentee_name}} on active engagement?

Consider quality of questions, curiosity, and constructive challenge.

Options:
1 = Not engaged
2 = Slightly engaged
3 = Moderately engaged
4 = Highly engaged
5 = Exceptionally engaged
```

### Question 3

Code: `respect_for_time`

```text
3/7. How would you rate {{mentee_name}} on punctuality and respect for time?

Options:
1 = Poor time discipline
2 = Below expectations
3 = Acceptable
4 = Respectful of time
5 = Excellent time discipline
```

### Question 4

Code: `ownership_mindset`

```text
4/7. How would you rate {{mentee_name}} on follow-through and ownership of their growth?

Options:
1 = Not demonstrated
2 = Rarely demonstrated
3 = Sometimes demonstrated
4 = Usually demonstrated
5 = Consistently demonstrated
```

### Question 5

Code: `reciprocal_value`

```text
5/7. Did {{mentee_name}} contribute useful insights or perspective that added value to the conversation?

Options:
1 = No added value
2 = Slight value
3 = Moderate value
4 = Strong value
5 = Exceptional value
```

### Question 6

Code: `recommend_continue`

```text
6/7. Would you be happy to continue mentorship with {{mentee_name}}?

Options:
1 = Yes
2 = No
```

### Question 7

Code: `optional_comment`

```text
7/7. Optional: share one short comment on what stood out or what should improve before the next session.

Optional field: add a short comment or leave it blank.
```

### Completion Message

```text
Thanks. Your review has been submitted.

We will reveal the review once both sides submit, or when the 48-hour review window closes.
```

## C. Fit Review After 3 Sessions

### Form Summary

- audience: both mentor and mentee
- target: the relationship, not only the last session
- answer scale: 1 to 5
- recommendation question: yes or no
- final comment: optional

### Preferred WhatsApp Flow Wireframe

```text
+------------------------------------------------------+
| ProsperMentor                                        |
| Rate Mentorship Fit                                  |
|------------------------------------------------------|
| You and {{other_party_name}} have completed          |
| 3 sessions together.                                 |
|                                                      |
| Help us decide whether to keep the match as-is       |
| or support a rematch.                                |
|                                                      |
|                          [ Start Review ]            |
+------------------------------------------------------+
                         |
                         v
+------------------------------------------------------+
| Step 1 of 2                                          |
|------------------------------------------------------|
| Overall fit score                                    |
| ( ) 1   ( ) 2   ( ) 3   ( ) 4   ( ) 5                |
|                                                      |
| Continue with this match?                            |
| ( ) Yes                                ( ) No        |
|                                                      |
|                                      [ Next ]        |
+------------------------------------------------------+
                         |
                         v
+------------------------------------------------------+
| Step 2 of 2                                          |
|------------------------------------------------------|
| Optional comment                                     |
| [_______________________________________________]    |
|                                                      |
| Examples:                                            |
| - what is working well                               |
| - what should change                                 |
| - whether a rematch may help                         |
|                                                      |
|                         [ Submit Review ]            |
+------------------------------------------------------+
```

### Intro Message

```text
You and {{other_party_name}} have now completed 3 sessions together.

Please rate the overall mentorship fit. This helps ProsperMentor decide whether to keep the match as-is or support a rematch.

Your answers remain private until both sides submit, or the 48-hour review window closes.

Click Start to begin.
```

### Question 1

Code: `fit_score`

```text
1/3. How would you rate the overall fit of this mentorship relationship so far?

Options:
1 = Very poor fit
2 = Weak fit
3 = Fair fit
4 = Strong fit
5 = Excellent fit
```

### Question 2

Code: `want_to_continue_with_same_match`

```text
2/3. Would you like to continue with this same mentor match?

Options:
1 = Yes
2 = No
```

### Question 3

Code: `optional_comment`

```text
3/3. Optional: share one short comment about what is working or what should change.

Optional field: add a short comment or leave it blank.
```

### Completion Message

```text
Thanks. Your fit review has been submitted.

We will reveal the outcome once both sides submit, or when the 48-hour review window closes.
```

## Blind Reveal Logic

The business rule is simple:

- neither side sees the other side's review before reveal
- review aggregates should only update from revealed reviews

Recommended cycle states:

- `OPEN`
- `IN_PROGRESS`
- `WAITING_FOR_COUNTERPART`
- `REVEALED`
- `EXPIRED_PARTIAL`
- `EXPIRED_EMPTY`

Recommended request states:

- `PENDING`
- `IN_PROGRESS`
- `SUBMITTED`
- `EXPIRED`

Reveal behavior:

- if both parties submit before 48 hours, reveal immediately
- if only one party submits, reveal that side's review at expiry
- if neither party submits, close the cycle with no score impact

## Reminder And Expiry Flow

Recommended cadence:

- send review invite immediately after session completion
- send reminder at 24 hours if request not submitted
- send final reminder at 44 hours if request not submitted
- expire at 48 hours

Reminder copy:

```text
Reminder: your ProsperMentor review for the session with {{other_party_name}} is still pending.

Your review closes in {{time_remaining}}.

Click Start to reopen the form.
```

Expiry copy:

```text
Your ProsperMentor review window for the session with {{other_party_name}} has now closed.

If you already submitted, thank you. If not, this review will not affect scores.
```

## Low Score And Risk Rules

Thresholds from the current product proposal:

- mentor overall session score below `4.0` triggers Prosper team review
- mentee overall session score below `3.5` triggers Prosper team review
- fit score `3` or lower triggers rematch review
- `recommend_continue = NO` from either side triggers rematch review

Operational behavior:

- do not automatically cancel a relationship on one low score
- create an internal alert and show it in admin reporting
- if both sides indicate poor fit, prioritize rematch outreach
- repeated low punctuality or repeated `NO` recommendations should escalate severity

## Frontend Implications

The web app should not duplicate the WhatsApp survey UI.

Instead it should show:

- review status: pending, submitted, revealed, expired
- session-level score summaries after reveal
- aggregate mentor and mentee scores
- fit review status after 3 shared sessions
- admin alerts for low scores and rematch risk

## Backend Data Model Alignment

Recommended domain objects:

- `review_cycle`
- `review_request`
- `review_question_definition`
- `review_answer`
- `profile_rating_aggregate`
- `review_alert`
- `whatsapp_review_event`

Recommended question metadata:

- `cycle_type`
- `reviewer_role`
- `question_code`
- `prompt_template`
- `answer_type`
- `scale_min`
- `scale_max`
- `weight`
- `display_order`
- `active`

Recommended request runtime fields:

- `current_question_code`
- `opened_at`
- `expires_at`
- `submitted_at`
- `revealed_at`
- `last_reminder_at`
- `provider_message_id`

## Nautix Data Connector Contract

The final submit step should use a Nautix flow data connector that POSTs the completed form payload to ProsperMentor.

Recommended ProsperMentor endpoint:

- `POST /api/v1/reviews/flow/submit`

Recommended connector header:

- `X-Review-Connector-Key: <REVIEW_CONNECTOR_API_KEY>`

Required hidden fields in the flow action data:

- `review_request_id`
- `review_token`
- `reviewer_role`
- `review_cycle_type`

Expected final payload shape:

```json
{
  "review_request_id": "uuid",
  "review_token": "opaque-token",
  "quality_of_guidance": 5,
  "listened_and_adapted": 4,
  "presence_and_punctuality": 5,
  "knowledge_generosity": 4,
  "space_for_my_insights": 5,
  "recommend_continue": true,
  "optional_comment": "Very practical session."
}
```

Mentor-to-mentee submissions use the mentor-side question codes instead. Fit reviews use:

- `fit_score`
- `want_to_continue_with_same_match`
- `optional_comment`

Connector behavior:

- the flow's final submit should include `complete: true`
- the connector should send the stripped business fields only
- ProsperMentor validates the token, stores answers, updates reveal state, and returns a small JSON acknowledgment

## Provider Template Set

At minimum, prepare these outbound template families:

- `prosper_session_review_invite_mentee`
- `prosper_session_review_invite_mentor`
- `prosper_fit_review_invite`
- `prosper_review_reminder`
- `prosper_review_expired`

The review is expected to return as one WhatsApp Flow submission payload rather than a question-by-question chat exchange.

## MVP Recommendation

Ship this in the first review release:

1. Two session review forms
2. One fit review form after 3 sessions
3. Blind reveal
4. 48-hour review window
5. Reminder schedule
6. Aggregate scores
7. Low-score alerts

Do not ship in MVP:

- admin-editable survey builder
- arbitrary question branching
- long-form essay feedback
- multiple review styles per employer

The point of the system is consistency, comparability, and operational clarity.
