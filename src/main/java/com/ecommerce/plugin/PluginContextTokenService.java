package com.ecommerce.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Mints the short-lived context JWTs handed to a plugin's iframe (and embedded in direct-action
 * payloads). HS256, signed with the app's signing secret, 5-minute expiry. Hand-rolled like
 * {@link PluginTokenSupport} — the claims are flat and the platform is the only issuer, so a JWT
 * library would be the first new dependency for no gain. The plugin SDK owns verification.
 */
@Service
public class PluginContextTokenService {
    static final long TTL_SECONDS = 300;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final ObjectMapper objectMapper;

    public PluginContextTokenService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ContextToken mint(
            PluginApp app, String surface, String resourceType, String resourceId, String userPublicId) {
        if (app.getSigningSecret() == null || app.getSigningSecret().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "App has no signing secret — re-register it to enable UI extensions");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(TTL_SECONDS);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "shoopy");
        claims.put("aud", app.getPublicAppId());
        claims.put("storeId", app.getStoreId());
        claims.put("surface", surface);
        if (resourceType != null && resourceId != null) {
            claims.put("resource", Map.of("type", resourceType, "id", resourceId));
        }
        if (userPublicId != null) {
            claims.put("userId", userPublicId);
        }
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());

        String header = B64.encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload;
        try {
            payload = B64.encodeToString(objectMapper.writeValueAsBytes(claims));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not build context token");
        }
        String signature = sign(header + "." + payload, app.getSigningSecret());
        return new ContextToken(header + "." + payload + "." + signature, expiresAt);
    }

    static String sign(String signingInput, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign context token", ex);
        }
    }

    public record ContextToken(String token, Instant expiresAt) {}
}
