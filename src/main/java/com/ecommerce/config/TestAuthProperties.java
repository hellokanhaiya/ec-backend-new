package com.ecommerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DEV / TEST ONLY.
 *
 * When {@link #enabled} is true, the configured {@link #email} can sign in with
 * a fixed {@link #code} — no real OTP is generated, hashed randomly, or emailed.
 * This exists purely so the app can be exercised locally without a working SMTP
 * inbox.
 *
 * <p><strong>This is an authentication backdoor. It MUST remain disabled in any
 * shared, staging or production environment.</strong> It defaults to
 * {@code false}; enable it only via the local {@code .env}
 * ({@code APP_TEST_AUTH_ENABLED=true}).
 */
@Component
@ConfigurationProperties(prefix = "app.test-auth")
public class TestAuthProperties {

    private boolean enabled = false;
    private String email = "kanha12345@gmail.com";
    private String code = "123456";

    /** True when the test bypass is enabled and the given email is the test account. */
    public boolean matchesEmail(String normalizedEmail) {
        return enabled
                && email != null
                && normalizedEmail != null
                && email.trim().equalsIgnoreCase(normalizedEmail.trim());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
