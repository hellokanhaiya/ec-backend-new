package com.ecommerce.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fetches, validates, and stores plugin manifests. Production apps must serve manifests over
 * https from a public host; dev-mode apps may use http/localhost so a plugin under development
 * registers straight off its local dev server.
 */
@Service
public class PluginManifestService {
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;

    private final PluginManifestValidator validator;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public PluginManifestService(PluginManifestValidator validator, ObjectMapper objectMapper) {
        this.validator = validator;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** Fetches and validates; returns the manifest plus its normalized JSON for storage. */
    public FetchedManifest fetch(String manifestUrl, boolean devMode) {
        URI uri = requireAllowedUrl(manifestUrl, devMode);
        String body;
        try {
            body = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Could not fetch manifest from " + manifestUrl + ": " + ex.getMessage());
        }
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manifest response was empty");
        }
        if (body.length() > MAX_MANIFEST_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manifest is too large (max 256 KB)");
        }

        PluginManifest manifest;
        try {
            manifest = objectMapper.readValue(body, PluginManifest.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manifest is not valid JSON");
        }
        validator.validate(manifest);
        requireAllowedUrl(manifest.appUrl(), devMode);

        try {
            return new FetchedManifest(manifest, objectMapper.writeValueAsString(manifest));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store manifest");
        }
    }

    public PluginManifest parseStored(String manifestJson) {
        if (manifestJson == null || manifestJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(manifestJson, PluginManifest.class);
        } catch (Exception ex) {
            return null; // A manifest that no longer parses contributes no extensions.
        }
    }

    /**
     * SSRF guard: the backend fetches manifests and posts direct-action payloads to plugin URLs,
     * so non-dev apps may only point at public https hosts.
     */
    URI requireAllowedUrl(String url, boolean devMode) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL: " + url);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must be http(s): " + url);
        }
        if (uri.getHost() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must have a host: " + url);
        }
        if (devMode) {
            return uri;
        }
        if (!scheme.equals("https")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Production plugin URLs must use https (enable dev mode for http)");
        }
        try {
            InetAddress address = InetAddress.getByName(uri.getHost());
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Production plugin URLs must resolve to a public host");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not resolve host: " + uri.getHost());
        }
        return uri;
    }

    public record FetchedManifest(PluginManifest manifest, String rawJson) {}
}
