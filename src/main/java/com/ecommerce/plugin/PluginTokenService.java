package com.ecommerce.plugin;

import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.plugin.PluginTokenDtos.TokenCreateRequest;
import com.ecommerce.plugin.PluginTokenDtos.TokenCreatedData;
import com.ecommerce.plugin.PluginTokenDtos.TokenSummaryData;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Issues, lists, and revokes plugin API tokens for a store. */
@Service
public class PluginTokenService {
    private final PluginAppRepository appRepository;
    private final PluginApiTokenRepository tokenRepository;

    public PluginTokenService(PluginAppRepository appRepository, PluginApiTokenRepository tokenRepository) {
        this.appRepository = appRepository;
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public TokenCreatedData create(StoreAccessScope scope, TokenCreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token name is required");
        }
        Set<String> scopes = normalizeScopes(request.scopes());

        // v1: every token belongs to its own store-private app (created here); a future
        // marketplace install flow will attach tokens to developer-owned apps instead.
        PluginApp app = new PluginApp();
        app.setStoreId(scope.storeId());
        app.setOrgId(scope.orgId());
        app.setName(request.name().trim());
        app.setCreatedByUserId(scope.userId());
        app = appRepository.save(app);

        String plaintext = PluginTokenSupport.generateToken();

        PluginApiToken token = new PluginApiToken();
        token.setAppId(app.getId());
        token.setStoreId(scope.storeId());
        token.setOrgId(scope.orgId());
        token.setName(request.name().trim());
        token.setTokenHash(PluginTokenSupport.hashToken(plaintext));
        token.setTokenPrefix(PluginTokenSupport.TOKEN_PREFIX);
        token.setLastFour(PluginTokenSupport.lastFour(plaintext));
        token.setScopes(String.join(",", scopes));
        if (request.expiresInDays() != null && request.expiresInDays() > 0) {
            token.setExpiresAt(Instant.now().plus(Duration.ofDays(request.expiresInDays())));
        }
        token.setCreatedByUserId(scope.userId());
        token = tokenRepository.save(token);

        return new TokenCreatedData(
                token.getPublicTokenId(),
                token.getName(),
                plaintext,
                PluginTokenSupport.maskToken(token.getTokenPrefix(), token.getLastFour()),
                List.copyOf(scopes),
                token.getExpiresAt(),
                token.getCreatedAt());
    }

    /**
     * Issues a token attached to an existing app (dev-register flow) instead of auto-creating a
     * private app the way {@link #create} does.
     */
    @Transactional
    public TokenCreatedData createForApp(PluginApp app, Long userId, List<String> requestedScopes) {
        Set<String> scopes = normalizeScopes(requestedScopes);
        String plaintext = PluginTokenSupport.generateToken();

        PluginApiToken token = new PluginApiToken();
        token.setAppId(app.getId());
        token.setStoreId(app.getStoreId());
        token.setOrgId(app.getOrgId());
        token.setName(app.getName());
        token.setTokenHash(PluginTokenSupport.hashToken(plaintext));
        token.setTokenPrefix(PluginTokenSupport.TOKEN_PREFIX);
        token.setLastFour(PluginTokenSupport.lastFour(plaintext));
        token.setScopes(String.join(",", scopes));
        token.setCreatedByUserId(userId);
        token = tokenRepository.save(token);

        return new TokenCreatedData(
                token.getPublicTokenId(),
                token.getName(),
                plaintext,
                PluginTokenSupport.maskToken(token.getTokenPrefix(), token.getLastFour()),
                List.copyOf(scopes),
                token.getExpiresAt(),
                token.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<TokenSummaryData> list(String storeId) {
        List<TokenSummaryData> summaries = new ArrayList<>();
        for (PluginApiToken token : tokenRepository.findByStoreIdOrderByCreatedAtDesc(storeId)) {
            summaries.add(toSummary(token));
        }
        return summaries;
    }

    @Transactional
    public TokenSummaryData revoke(String storeId, String publicTokenId) {
        PluginApiToken token = tokenRepository.findByStoreIdAndPublicTokenId(storeId, publicTokenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found"));
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(Instant.now());
            token = tokenRepository.save(token);
        }
        return toSummary(token);
    }

    private Set<String> normalizeScopes(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one scope is required");
        }
        Set<String> scopes = new LinkedHashSet<>();
        for (String scope : requested) {
            String key = scope == null ? "" : scope.trim();
            if (!PluginScopeCatalog.isValid(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown scope: " + key);
            }
            scopes.add(key);
        }
        return scopes;
    }

    private TokenSummaryData toSummary(PluginApiToken token) {
        return new TokenSummaryData(
                token.getPublicTokenId(),
                token.getName(),
                PluginTokenSupport.maskToken(token.getTokenPrefix(), token.getLastFour()),
                List.of(token.getScopes().split(",")),
                token.getExpiresAt(),
                token.getRevokedAt(),
                token.getLastUsedAt(),
                token.getCreatedAt());
    }
}
