package com.ecommerce.service;

import com.ecommerce.controller.PublicGeoController.GeoContext;
import com.ecommerce.controller.PublicGeoController.GeoLoginMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeoLocationService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper;

    @Value("${app.geo.lookup-base-url:https://ipapi.co}")
    private String lookupBaseUrl;

    @Value("${app.geo.public-ip-url:https://api.ipify.org?format=json}")
    private String publicIpUrl;

    public GeoLocationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeoContext resolve(HttpServletRequest request, String requestedIp) {
        String candidateIp = resolveCandidateIp(requestedIp, request);
        if (isLocalAddress(candidateIp)) {
            String publicIp = fetchPublicIp();
            if (publicIp != null && !publicIp.isBlank() && !isLocalAddress(publicIp)) {
                candidateIp = publicIp;
            }
        }

        return lookupGeoContext(candidateIp);
    }

    private String resolveCandidateIp(String requestedIp, HttpServletRequest request) {
        if (requestedIp != null && !requestedIp.isBlank()) {
            return requestedIp.trim();
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private GeoContext lookupGeoContext(String ip) {
        if (ip == null || ip.isBlank()) {
            return fallbackContext("unknown");
        }

        try {
            URI uri = URI.create(normalizeLookupBaseUrl() + "/" + ip + "/json/");
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return fallbackContext(ip);
            }

            JsonNode payload = objectMapper.readTree(response.body());
            if (payload.path("error").asBoolean(false)) {
                return fallbackContext(ip);
            }

            String countryCode = text(payload, "country_code", "XX");
            boolean india = "IN".equalsIgnoreCase(countryCode);
            String timezone = text(payload, "timezone", "UTC");

            return new GeoContext(
                    text(payload, "ip", ip),
                    countryCode,
                    text(payload, "country_name", "Unknown"),
                    continentName(text(payload, "continent_code", "")),
                    text(payload, "region", ""),
                    text(payload, "city", ""),
                    timezone,
                    timezone,
                    india,
                    india ? GeoLoginMode.EMAIL_AND_PHONE : GeoLoginMode.EMAIL_ONLY,
                    india ? List.of("EMAIL", "PHONE") : List.of("EMAIL"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return fallbackContext(ip);
        } catch (IOException ex) {
            return fallbackContext(ip);
        } catch (Exception ex) {
            return fallbackContext(ip);
        }
    }

    private String normalizeLookupBaseUrl() {
        return lookupBaseUrl.endsWith("/") ? lookupBaseUrl.substring(0, lookupBaseUrl.length() - 1) : lookupBaseUrl;
    }

    private String fetchPublicIp() {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(publicIpUrl))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "";
            }

            JsonNode payload = objectMapper.readTree(response.body());
            return text(payload, "ip", "");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean isLocalAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }

        String lowered = ip.toLowerCase(Locale.ROOT);
        return lowered.equals("127.0.0.1")
                || lowered.equals("::1")
                || lowered.equals("0:0:0:0:0:0:0:1")
                || lowered.startsWith("10.")
                || lowered.startsWith("192.168.")
                || lowered.startsWith("172.16.")
                || lowered.startsWith("172.17.")
                || lowered.startsWith("172.18.")
                || lowered.startsWith("172.19.")
                || lowered.startsWith("172.2")
                || lowered.startsWith("172.30.")
                || lowered.startsWith("172.31.")
                || lowered.equals("localhost");
    }

    private GeoContext fallbackContext(String ip) {
        String resolvedIp = ip == null || ip.isBlank() ? "unknown" : ip;
        return new GeoContext(
                resolvedIp,
                "XX",
                "Unknown",
                "",
                "",
                "",
                "UTC",
                "UTC",
                false,
                GeoLoginMode.EMAIL_ONLY,
                List.of("EMAIL"));
    }

    private String text(JsonNode payload, String field, String defaultValue) {
        String value = payload.path(field).asText("");
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String continentName(String continentCode) {
        return switch (continentCode == null ? "" : continentCode.toUpperCase(Locale.ROOT)) {
            case "AF" -> "Africa";
            case "AN" -> "Antarctica";
            case "AS" -> "Asia";
            case "EU" -> "Europe";
            case "NA" -> "North America";
            case "OC" -> "Oceania";
            case "SA" -> "South America";
            default -> continentCode == null || continentCode.isBlank() ? "" : continentCode;
        };
    }
}
