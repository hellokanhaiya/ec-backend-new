package com.ecommerce.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

final class AuthSupport {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AuthSupport() {}

    static String normalizeIdentifier(AuthChannel channel, String identifier) {
        if (identifier == null) {
            return "";
        }

        String trimmed = identifier.trim();
        return switch (channel) {
            case EMAIL -> trimmed.toLowerCase(Locale.ROOT);
            case PHONE, WHATSAPP -> trimmed.replaceAll("[^0-9]", "");
        };
    }

    static boolean isValidIdentifier(AuthChannel channel, String identifier) {
        String normalized = normalizeIdentifier(channel, identifier);
        return switch (channel) {
            case EMAIL -> normalized.contains("@") && normalized.contains(".");
            case PHONE, WHATSAPP -> normalized.matches("\\d{8,15}");
        };
    }

    static String maskIdentifier(AuthChannel channel, String identifier) {
        String normalized = normalizeIdentifier(channel, identifier);
        return switch (channel) {
            case EMAIL -> {
                int at = normalized.indexOf('@');
                if (at <= 1) {
                    yield "***" + normalized.substring(Math.max(0, at));
                }
                String name = normalized.substring(0, at);
                String domain = normalized.substring(at);
                yield name.charAt(0) + "***" + domain;
            }
            case PHONE, WHATSAPP -> {
                if (normalized.length() <= 4) {
                    yield "***" + normalized;
                }
                yield "+***" + normalized.substring(normalized.length() - 4);
            }
        };
    }

    static String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hashOtp(String requestKey, String otp) {
        return hash(requestKey + ":" + otp);
    }

    static String hashToken(String token) {
        return hash(token);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash value", ex);
        }
    }

    static boolean isExpired(Instant expiresAt) {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    static int resendDelaySeconds(int requestCount) {
        return switch (requestCount) {
            case 1 -> 30;
            case 2 -> 60;
            case 3 -> 120;
            case 4 -> 180;
            default -> 300;
        };
    }
}
