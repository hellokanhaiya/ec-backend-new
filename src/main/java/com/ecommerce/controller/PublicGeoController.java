package com.ecommerce.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.ecommerce.service.GeoLocationService;

@RestController
@RequestMapping("/api/public")
public class PublicGeoController {
    private final GeoLocationService geoLocationService;

    @Value("${app.geo.device-token:st_tkn_8f92a1b7c3d4e5f6}")
    private String expectedDeviceToken;

    public PublicGeoController(GeoLocationService geoLocationService) {
        this.geoLocationService = geoLocationService;
    }

    @GetMapping("/geo")
    public ResponseEntity<ApiResponse<GeoContext>> geo(
            @RequestParam(required = false) String ip,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken,
            HttpServletRequest request) {
        validateAccess(deviceToken, request);
        GeoContext context = geoLocationService.resolve(request, ip);

        return ResponseEntity.ok(new ApiResponse<>(true, "Geo context resolved", context));
    }

    private void validateAccess(String deviceToken, HttpServletRequest request) {
        if (expectedDeviceToken == null || expectedDeviceToken.isBlank()) {
            return;
        }

        if (expectedDeviceToken.equals(deviceToken) || isLocalRequest(request)) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token");
    }

    private boolean isLocalRequest(HttpServletRequest request) {
        return isLocalHostValue(request.getHeader("Origin"))
                || isLocalHostValue(request.getHeader("Referer"))
                || isLocalHostValue(request.getServerName());
    }

    private boolean isLocalHostValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String host = extractHost(value);
        if (host == null || host.isBlank()) {
            host = value;
        }

        String lowered = host.toLowerCase(Locale.ROOT);
        if (lowered.contains("localhost") || lowered.contains("127.0.0.1") || lowered.contains("::1")) {
            return true;
        }
        if (lowered.startsWith("192.168.") || lowered.startsWith("10.")) {
            return true;
        }
        if (lowered.startsWith("172.")) {
            String[] parts = lowered.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isSiteLocalAddress();
        } catch (UnknownHostException ignored) {
            return false;
        }
    }

    private String extractHost(String value) {
        try {
            if (value.contains("://")) {
                URI uri = URI.create(value);
                return uri.getHost();
            }
        } catch (IllegalArgumentException ignored) {
            // fall back to raw value below
        }
        return value;
    }

    public record ApiResponse<T>(boolean success, String message, T data) {
    }

    public enum GeoLoginMode {
        EMAIL_ONLY,
        EMAIL_AND_PHONE
    }

    public record GeoContext(
            String ip,
            String countryCode,
            String countryName,
            String continent,
            String region,
            String city,
            String timezone,
            String zone,
            boolean india,
            GeoLoginMode loginMode,
            List<String> authMethodsAllowed) {
    }
}
