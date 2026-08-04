# Employee Invitation WhatsApp Template

Template name: `prosper_employee_invitation`

Category: `UTILITY`

Language: `en_US`

## Body

```text
Hello,

{{1}} has invited you to join {{2}}, a mentorship platform that connects you with expert mentors to support your career growth and professional development.

Accept your invitation and create your account here:
{{3}}

Invitation details:
Company: {{1}}
Email: {{4}}

What you will get:
- Expert mentorship
- Personalized programs
- Flexible scheduling
- Company-sponsored access

This invitation link expires in 7 days. If you need help or a new invitation, please contact your company administrator or {{5}}.
```

## Body Parameter Order

1. `company.name`
2. `appName`
3. `invitationUrl`
4. `employeeEmail`
5. `supportEmail`

## Notes

- This draft follows the same message structure as the email template in `templates/email/employee-invitation.html`.
- It avoids first-name personalization because the current employee invitation flow only has the company, email, app name, and invitation link available.
- The invitation URL is included directly in the body because the current Nautix sender only passes `bodyParameters`.
