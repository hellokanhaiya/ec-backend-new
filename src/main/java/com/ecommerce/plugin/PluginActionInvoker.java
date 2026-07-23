package com.ecommerce.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Executes {@code mode: "direct"} actions: the backend POSTs an HMAC-signed context payload to
 * the plugin's declared URL and streams whatever comes back (typically a PDF) to the browser.
 * The plugin verifies {@code X-Shoopy-Hmac-Sha256} (hex HMAC of the raw body with its signing
 * secret) before trusting the request.
 */
@Service
public class PluginActionInvoker {
    public static final String HMAC_HEADER = "X-Shoopy-Hmac-Sha256";
    private static final int MAX_RESPONSE_BYTES = 20 * 1024 * 1024;

    private final PluginManifestService manifestService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public PluginActionInvoker(PluginManifestService manifestService, ObjectMapper objectMapper) {
        this.manifestService = manifestService;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public ResponseEntity<byte[]> invoke(
            PluginApp app, PluginManifest.Extension extension, String contextToken, Map<String, Object> resource) {
        if (app.getAppUrl() == null || app.getSigningSecret() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "App has no appUrl/signing secret");
        }
        URI target = manifestService.requireAllowedUrl(joinUrl(app.getAppUrl(), extension.url()), app.isDevMode());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("extensionId", extension.id());
        payload.put("storeId", app.getStoreId());
        payload.put("resource", resource);
        payload.put("contextToken", contextToken);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(payload);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not build action payload");
        }

        ResponseEntity<byte[]> response;
        try {
            response = restClient.post()
                    .uri(target)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HMAC_HEADER, hmacHex(body, app.getSigningSecret()))
                    .body(body)
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Plugin action failed: " + ex.getMessage());
        }

        byte[] responseBody = response.getBody() == null ? new byte[0] : response.getBody();
        if (responseBody.length > MAX_RESPONSE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Plugin action response is too large");
        }

        // Pass through only the content headers the browser download needs.
        HttpHeaders headers = new HttpHeaders();
        MediaType contentType = response.getHeaders().getContentType();
        headers.setContentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : contentType);
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (disposition != null) {
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition);
        }
        headers.setContentLength(responseBody.length);
        return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
    }

    static String hmacHex(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign action payload", ex);
        }
    }

    private String joinUrl(String base, String path) {
        String trimmedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmedBase + path;
    }
}
