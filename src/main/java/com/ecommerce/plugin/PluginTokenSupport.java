package com.ecommerce.plugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Token generation and hashing for plugin API tokens. Same recipe as the auth session tokens
 * ({@code AuthSupport} — package-private to {@code com.ecommerce.auth}, hence duplicated here):
 * 256-bit SecureRandom, base64url, SHA-256 hex digest for storage.
 */
final class PluginTokenSupport {
    static final String TOKEN_PREFIX = "sk_plg_";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PluginTokenSupport() {}

    static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static boolean isPluginToken(String token) {
        return token != null && token.startsWith(TOKEN_PREFIX);
    }

    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash value", ex);
        }
    }

    static String lastFour(String token) {
        return token.substring(token.length() - 4);
    }

    static String maskToken(String tokenPrefix, String lastFour) {
        return tokenPrefix + "····" + lastFour;
    }
}
