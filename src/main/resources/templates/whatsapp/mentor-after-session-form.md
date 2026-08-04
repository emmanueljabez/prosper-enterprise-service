# Mentor After Session Form WhatsApp Template

Template name: `prosper_mentor_after_session_form`

Category: `UTILITY`

Language: `en_US`

## Body

```text
Hi {{1}}. Your session with {{2}} has been completed.

Please rate the session. Your answers remain private until both of you submit, or the 48-hour review window closes.

Click Start to begin.
```

## Body Parameter Order

1. `reviewer.firstName`
2. `target.fullName`

## Notes

- This template is used when the mentor is asked to rate the mentee after a completed session.
- The `Start` button is assumed to be configured in the WhatsApp template itself.
- The backend currently sends body parameters only through Nautix, so button configuration lives in the provider template setup.
- The FLOW button is expected to receive hidden action data containing:
  - `review_request_id`
  - `review_token`
  - `reviewer_role`
  - `review_cycle_type`
