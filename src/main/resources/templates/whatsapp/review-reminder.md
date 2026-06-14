# Review Reminder WhatsApp Template

Template name: `prosper_review_reminder`

Category: `UTILITY`

Language: `en_US`

## Body

```text
Reminder: your ProsperMentor review for the session with {{1}} is still pending.

Your review closes in {{2}}.

Click Start to reopen the form.
```

## Body Parameter Order

1. `target.fullName`
2. `timeRemainingLabel`

## Notes

- This template is used for both first and final reminders.
- Current reminder labels sent by the backend are:
  - `about 24 hours`
  - `about 4 hours`
- The FLOW button should reopen the original review form using the same hidden action data:
  - `review_request_id`
  - `review_token`
  - `reviewer_role`
  - `review_cycle_type`
