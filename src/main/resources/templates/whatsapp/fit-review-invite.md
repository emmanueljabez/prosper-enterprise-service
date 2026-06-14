# Fit Review Invite WhatsApp Template

Template name: `prosper_fit_review_invite`

Category: `UTILITY`

Language: `en_US`

## Body

```text
Hi {{1}}.

You and {{2}} have now completed 3 sessions together.

Please rate the overall mentorship fit. Your answers remain private until both of you submit, or the 48-hour review window closes.

Click Start to begin.
```

## Body Parameter Order

1. `reviewer.firstName`
2. `target.fullName`

## Notes

- This template is used for both mentor-side and mentee-side fit reviews after the third completed shared session.
- The FLOW button should reopen the fit review form.
- Hidden action data should include:
  - `review_request_id`
  - `review_token`
  - `reviewer_role`
  - `review_cycle_type`
