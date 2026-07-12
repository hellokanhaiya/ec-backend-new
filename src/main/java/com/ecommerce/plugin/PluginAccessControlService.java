package com.ecommerce.plugin;

import com.ecommerce.auth.BusinessStoreData;
import com.ecommerce.store.BusinessStoreService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single authorization chokepoint for the {@code /api/v1/plugin/**} surface — the plugin-token
 * twin of {@link com.ecommerce.access.AccessControlService}. Only {@code sk_plg_} tokens resolve
 * here; dashboard session tokens get {@code 401}. Throws {@code 401} for a bad/revoked/expired
 * token or disabled app, {@code 403} when the token lacks the required scope.
 */
@Service
public class PluginAccessControlService {
    private static final Duration LAST_USED_UPDATE_INTERVAL = Duration.ofMinutes(1);

    private final PluginApiTokenRepository tokenRepository;
    private final PluginAppRepository appRepository;
    private final BusinessStoreService businessStoreService;

    public PluginAccessControlService(
            PluginApiTokenRepository tokenRepository,
            PluginAppRepository appRepository,
            BusinessStoreService businessStoreService) {
        this.tokenRepository = tokenRepository;
        this.appRepository = appRepository;
        this.businessStoreService = businessStoreService;
    }

    @Transactional
    public PluginAccessScope requirePluginScope(String authorization, String scopeKey) {
        String token = extractBearerToken(authorization);
        if (!PluginTokenSupport.isPluginToken(token)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "A plugin API token (sk_plg_...) is required for this endpoint");
        }

        PluginApiToken apiToken = tokenRepository.findByTokenHash(PluginTokenSupport.hashToken(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid plugin API token"));
        if (apiToken.getRevokedAt() != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Plugin API token has been revoked");
        }
        if (apiToken.getExpiresAt() != null && Instant.now().isAfter(apiToken.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Plugin API token has expired");
        }

        PluginApp app = appRepository.findById(apiToken.getAppId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Plugin app not found"));
        if (!PluginApp.STATUS_ACTIVE.equals(app.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Plugin app has been disabled");
        }

        Set<String> scopes = new LinkedHashSet<>(Set.of(apiToken.getScopes().split(",")));
        if (!scopes.contains(scopeKey)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Token is missing the required scope: " + scopeKey);
        }

        touchLastUsed(apiToken);

        // Resolve owner id + country from the store so plugin reads can reuse the same
        // storeId-scoped service methods the dashboard uses (some seed defaults off the owner id).
        Optional<BusinessStoreData> store = businessStoreService.findStoreByStoreId(apiToken.getStoreId());
        String ownerPublicUserId = store.map(BusinessStoreData::publicUserId).orElse(null);
        String countryCode = store.map(BusinessStoreData::countryCode).orElse(null);

        return new PluginAccessScope(
                apiToken.getStoreId(),
                apiToken.getOrgId(),
                ownerPublicUserId,
                countryCode,
                app.getId(),
                app.getPublicAppId(),
                apiToken.getId(),
                scopes);
    }

    // Throttled to roughly one write per minute so hot tokens don't hammer the row.
    private void touchLastUsed(PluginApiToken token) {
        Instant now = Instant.now();
        if (token.getLastUsedAt() == null
                || token.getLastUsedAt().isBefore(now.minus(LAST_USED_UPDATE_INTERVAL))) {
            token.setLastUsedAt(now);
            tokenRepository.save(token);
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is required");
        }

        String value = authorizationHeader.trim();
        if (!value.toLowerCase().startsWith("bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }

        String token = value.substring(7).trim();
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }

        return token;
    }
}
