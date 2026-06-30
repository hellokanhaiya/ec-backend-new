# Email OTP setup

The backend uses Spring Mail (`spring-boot-starter-mail`) with `JavaMailSender`.

## Local development

1. Create a `.env` file in the backend root and set these values for Mailtrap Sandbox:
   ```bash
   MAIL_HOST=sandbox.smtp.mailtrap.io
   MAIL_PORT=2525
   MAIL_USERNAME=40a0ac64810b7b
   MAIL_PASSWORD=<your-mailtrap-sandbox-password>
   MAIL_SMTP_AUTH=true
   MAIL_SMTP_STARTTLS=true
   APP_MAIL_FROM=no-reply@ecommerce.local
   ```
2. Restart the backend.
3. Open the Mailtrap Sandbox inbox and check the captured email there.

> Mailtrap Sandbox is for testing only. It does not deliver to real inboxes.

## Production

Use Amazon SES SMTP for low-cost delivery:

```bash
MAIL_HOST=email-smtp.us-east-1.amazonaws.com
MAIL_PORT=587
MAIL_USERNAME=<ses-smtp-username>
MAIL_PASSWORD=<ses-smtp-password>
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
APP_MAIL_FROM=<verified-sender@yourdomain.com>
```

## Notes

- If `MAIL_HOST` is not set, OTP generation still works in debug mode and the OTP is logged.
- Mailtrap Sandbox captures the email inside Mailtrap instead of sending it to Gmail or Outlook.
- To make email delivery strict, switch the fallback to a hard failure in `OtpDeliveryService`.
