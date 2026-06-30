package com.ecommerce.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
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
        return containsLocalhost(request.getHeader("Origin"))
                || containsLocalhost(request.getHeader("Referer"))
                || containsLocalhost(request.getServerName());
    }

    private boolean containsLocalhost(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String lowered = value.toLowerCase(Locale.ROOT);
        return lowered.contains("localhost") || lowered.contains("127.0.0.1") || lowered.contains("::1");
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
