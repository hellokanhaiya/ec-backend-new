package com.ecommerce.plugin;

import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.plugin.PluginAppDtos.ActionInvokeRequest;
import com.ecommerce.plugin.PluginAppDtos.AppSummaryData;
import com.ecommerce.plugin.PluginAppDtos.ContextTokenData;
import com.ecommerce.plugin.PluginAppDtos.ContextTokenRequest;
import com.ecommerce.plugin.PluginAppDtos.DevRegisterRequest;
import com.ecommerce.plugin.PluginAppDtos.DevRegisteredData;
import com.ecommerce.plugin.PluginAppDtos.ExtensionFeedItem;
import com.ecommerce.plugin.PluginManifestService.FetchedManifest;
import com.ecommerce.plugin.PluginTokenDtos.TokenCreatedData;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registers manifest-bearing plugin apps and serves their extensions to the admin UI. Dev
 * registration is deliberately one-shot: app + scoped token + signing secret in a single call so
 * a plugin developer goes from `npm run dev` to a live extension with one paste.
 */
@Service
public class PluginAppService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PluginAppRepository appRepository;
    private final PluginManifestService manifestService;
    private final PluginTokenService tokenService;
    private final PluginContextTokenService contextTokenService;
    private final PluginActionInvoker actionInvoker;

    public PluginAppService(
            PluginAppRepository appRepository,
            PluginManifestService manifestService,
            PluginTokenService tokenService,
            PluginContextTokenService contextTokenService,
            PluginActionInvoker actionInvoker) {
        this.appRepository = appRepository;
        this.manifestService = manifestService;
        this.tokenService = tokenService;
        this.contextTokenService = contextTokenService;
        this.actionInvoker = actionInvoker;
    }

    @Transactional
    public DevRegisteredData devRegister(StoreAccessScope scope, DevRegisterRequest request) {
        if (request == null || request.manifestUrl() == null || request.manifestUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifestUrl is required");
        }
        FetchedManifest fetched = manifestService.fetch(request.manifestUrl().trim(), true);
        PluginManifest manifest = fetched.manifest();

        PluginApp app = new PluginApp();
        app.setStoreId(scope.storeId());
        app.setOrgId(scope.orgId());
        app.setName(request.name() != null && !request.name().isBlank()
                ? request.name().trim()
                : manifest.name());
        app.setDescription(manifest.description());
        app.setCreatedByUserId(scope.userId());
        app.setDevMode(true);
        app.setSigningSecret(generateSigningSecret());
        applyManifest(app, request.manifestUrl().trim(), fetched);
        app = appRepository.save(app);

        TokenCreatedData token = tokenService.createForApp(app, scope.userId(), manifest.scopes());
        return new DevRegisteredData(toSummary(app), token, app.getSigningSecret());
    }

    @Transactional
    public AppSummaryData refreshManifest(String storeId, String publicAppId) {
        PluginApp app = require(storeId, publicAppId);
        if (app.getManifestUrl() == null || app.getManifestUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "App has no manifest URL to refresh");
        }
        FetchedManifest fetched = manifestService.fetch(app.getManifestUrl(), app.isDevMode());
        applyManifest(app, app.getManifestUrl(), fetched);
        return toSummary(appRepository.save(app));
    }

    @Transactional
    public AppSummaryData setStatus(String storeId, String publicAppId, String status) {
        if (!PluginApp.STATUS_ACTIVE.equals(status) && !PluginApp.STATUS_DISABLED.equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "status must be ACTIVE or DISABLED");
        }
        PluginApp app = require(storeId, publicAppId);
        app.setStatus(status);
        return toSummary(appRepository.save(app));
    }

    @Transactional(readOnly = true)
    public List<AppSummaryData> list(String storeId) {
        List<AppSummaryData> apps = new ArrayList<>();
        for (PluginApp app : appRepository.findByStoreIdOrderByCreatedAtDesc(storeId)) {
            // Token-only apps (created via Settings → API tokens) carry no manifest; the Apps
            // page lists only real UI-bearing plugins.
            if (app.getManifestJson() != null) {
                apps.add(toSummary(app));
            }
        }
        return apps;
    }

    /** Everything the admin UI needs to render plugin contributions, in one call. */
    @Transactional(readOnly = true)
    public List<ExtensionFeedItem> extensionsFeed(String storeId) {
        List<ExtensionFeedItem> items = new ArrayList<>();
        for (PluginApp app : appRepository.findByStoreIdAndStatusOrderByCreatedAtDesc(
                storeId, PluginApp.STATUS_ACTIVE)) {
            PluginManifest manifest = manifestService.parseStored(app.getManifestJson());
            if (manifest == null || manifest.extensions() == null) {
                continue;
            }
            for (PluginManifest.Extension extension : manifest.extensions()) {
                items.add(new ExtensionFeedItem(
                        app.getPublicAppId(), app.getName(), app.getAppUrl(), app.isDevMode(), extension));
            }
        }
        return items;
    }

    @Transactional(readOnly = true)
    public ContextTokenData mintContextToken(
            String storeId, String publicAppId, ContextTokenRequest request, String userPublicId) {
        PluginApp app = requireActive(storeId, publicAppId);
        if (request == null || request.surface() == null || request.surface().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "surface is required");
        }
        PluginContextTokenService.ContextToken token = contextTokenService.mint(
                app, request.surface().trim(), request.resourceType(), request.resourceId(), userPublicId);
        return new ContextTokenData(token.token(), token.expiresAt());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> invokeAction(
            String storeId, String publicAppId, String extensionId, ActionInvokeRequest request,
            String userPublicId) {
        PluginApp app = requireActive(storeId, publicAppId);
        PluginManifest manifest = manifestService.parseStored(app.getManifestJson());
        if (manifest == null || manifest.extensions() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "App has no extensions");
        }
        PluginManifest.Extension extension = manifest.extensions().stream()
                .filter(e -> PluginManifest.EXT_ORDER_DETAIL_ACTION.equals(e.type())
                        && extensionId.equals(e.id()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Extension not found"));
        String mode = extension.mode() == null ? PluginManifest.MODE_MODAL : extension.mode();
        if (!PluginManifest.MODE_DIRECT.equals(mode)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Extension is not a direct action — open it as a modal instead");
        }

        String resourceType = request == null ? null : request.resourceType();
        String resourceId = request == null ? null : request.resourceId();
        PluginContextTokenService.ContextToken token = contextTokenService.mint(
                app, extension.type(), resourceType, resourceId, userPublicId);

        Map<String, Object> resource = new LinkedHashMap<>();
        if (resourceType != null && resourceId != null) {
            resource.put("type", resourceType);
            resource.put("id", resourceId);
        }
        return actionInvoker.invoke(app, extension, token.token(), resource);
    }

    private void applyManifest(PluginApp app, String manifestUrl, FetchedManifest fetched) {
        app.setManifestUrl(manifestUrl);
        app.setManifestJson(fetched.rawJson());
        app.setManifestVersion(fetched.manifest().version());
        app.setAppUrl(fetched.manifest().appUrl());
        if (app.getDescription() == null && fetched.manifest().description() != null) {
            app.setDescription(fetched.manifest().description());
        }
    }

    private PluginApp require(String storeId, String publicAppId) {
        return appRepository.findByStoreIdAndPublicAppId(storeId, publicAppId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "App not found"));
    }

    private PluginApp requireActive(String storeId, String publicAppId) {
        PluginApp app = require(storeId, publicAppId);
        if (!PluginApp.STATUS_ACTIVE.equals(app.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "App is disabled");
        }
        return app;
    }

    private AppSummaryData toSummary(PluginApp app) {
        PluginManifest manifest = manifestService.parseStored(app.getManifestJson());
        return new AppSummaryData(
                app.getPublicAppId(),
                app.getName(),
                app.getDescription(),
                app.getStatus(),
                app.isDevMode(),
                app.getAppUrl(),
                app.getManifestUrl(),
                app.getManifestVersion(),
                manifest == null || manifest.scopes() == null ? List.of() : manifest.scopes(),
                manifest == null || manifest.extensions() == null ? 0 : manifest.extensions().size(),
                app.getCreatedAt(),
                app.getUpdatedAt());
    }

    private String generateSigningSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "plgsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
