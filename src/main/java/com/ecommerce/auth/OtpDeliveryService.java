package com.ecommerce.auth;

import java.nio.charset.StandardCharsets;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class OtpDeliveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OtpDeliveryService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.from:no-reply@ecommerce.local}")
    private String fromAddress;

    public OtpDeliveryService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void deliver(AuthChannel channel, String destination, String otpCode) {
        switch (channel) {
            case EMAIL -> sendEmail(destination, otpCode);
            case PHONE -> LOGGER.info("SMS OTP prepared for {} -> {}", destination, otpCode);
            case WHATSAPP -> LOGGER.info("WhatsApp OTP prepared for {} -> {}", destination, otpCode);
        }
    }

    private void sendEmail(String email, String otpCode) {
        if (mailHost == null || mailHost.isBlank()) {
            LOGGER.warn(
                    "SMTP host is not configured. Debug mode OTP for {}: {}",
                    email, otpCode);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(email);
            helper.setSubject("Your OTP code");
            helper.setText(buildOtpEmailBody(otpCode), true);
            mailSender.send(message);
            LOGGER.info("Email OTP sent to {}", email);
        } catch (MessagingException exception) {
            throw new IllegalStateException("Unable to compose OTP email", exception);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to send OTP email to {}: {}", email, exception.getMessage(), exception);
            throw new IllegalStateException("Unable to send OTP email", exception);
        }
    }

    private String buildOtpEmailBody(String otpCode) {
        return """
                <html>
                  <body style="font-family:Arial,sans-serif;background:#f8fafc;padding:24px;color:#0f172a;">
                    <div style="max-width:480px;margin:0 auto;background:#fff;border-radius:16px;padding:24px;border:1px solid #e2e8f0;">
                      <h2 style="margin:0 0 12px;font-size:20px;">Your verification code</h2>
                      <p style="margin:0 0 20px;line-height:1.6;">Use this OTP to continue signing in to your account.</p>
                      <div style="font-size:28px;letter-spacing:8px;font-weight:700;background:#eef2ff;padding:16px 20px;border-radius:12px;text-align:center;">%s</div>
                      <p style="margin:20px 0 0;color:#475569;font-size:12px;">This code expires soon. If you did not request it, you can ignore this email.</p>
                    </div>
                  </body>
                </html>
                """.formatted(otpCode);
    }
}
