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
import java.util.ArrayList;
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

    @Value("${app.geo.lookup-base-url:https://api.techniknews.net/ipgeo}")
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

        List<URI> candidateUris = buildCandidateUris(ip);
        for (URI uri : candidateUris) {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .header("Accept", "application/json")
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    continue;
                }

                JsonNode payload = objectMapper.readTree(response.body());
                if (isFailurePayload(payload)) {
                    continue;
                }

                GeoContext resolved = mapGeoPayload(payload, ip);
                if (resolved != null) {
                    return resolved;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return fallbackContext(ip);
            } catch (IOException ex) {
                // try next provider
            } catch (Exception ex) {
                // try next provider
            }
        }

        return fallbackContext(ip);
    }

    private List<URI> buildCandidateUris(String ip) {
        String baseUrl = normalizeLookupBaseUrl();
        List<URI> uris = new ArrayList<>();
        uris.add(buildProviderUri(baseUrl, ip));

        String technikNews = "https://api.techniknews.net/ipgeo";
        if (!baseUrl.equalsIgnoreCase(technikNews)) {
            uris.add(buildProviderUri(technikNews, ip));
        }

        String geoJs = "https://get.geojs.io/v1/ip/geo";
        if (!baseUrl.equalsIgnoreCase(geoJs)) {
            uris.add(URI.create(geoJs + "/" + ip + ".json"));
        }

        String ipWhoIs = "https://ipwho.is";
        if (!baseUrl.equalsIgnoreCase(ipWhoIs)) {
            uris.add(buildProviderUri(ipWhoIs, ip));
        }

        return uris;
    }

    private URI buildProviderUri(String baseUrl, String ip) {
        if (baseUrl.contains("get.geojs.io")) {
            return URI.create(trimTrailingSlash(baseUrl) + "/" + ip + ".json");
        }
        return URI.create(trimTrailingSlash(baseUrl) + "/" + ip);
    }

    private String normalizeLookupBaseUrl() {
        String baseUrl = lookupBaseUrl == null || lookupBaseUrl.isBlank() ? "https://api.techniknews.net/ipgeo" : lookupBaseUrl.trim();
        return trimTrailingSlash(baseUrl);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean isFailurePayload(JsonNode payload) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return true;
        }

        if (payload.path("error").asBoolean(false)) {
            return true;
        }

        String status = text(payload, "status", "");
        return "fail".equalsIgnoreCase(status);
    }

    private GeoContext mapGeoPayload(JsonNode payload, String fallbackIp) {
        String countryCode = text(payload, "country_code", "countryCode", "XX");
        String countryName = text(payload, "country_name", "country", "Unknown");
        String continent = text(payload, "continent", continentName(text(payload, "continent_code", "continentCode", "")));
        String region = text(payload, "region", "regionName", "");
        String city = text(payload, "city", "cityName", "");
        String timezone = resolveTimezone(payload);
        boolean india = "IN".equalsIgnoreCase(countryCode);

        return new GeoContext(
                text(payload, "ip", fallbackIp),
                countryCode,
                countryName,
                continent,
                region,
                city,
                timezone,
                timezone,
                india,
                india ? GeoLoginMode.EMAIL_AND_PHONE : GeoLoginMode.EMAIL_ONLY,
                india ? List.of("EMAIL", "PHONE") : List.of("EMAIL"));
    }

    private String resolveTimezone(JsonNode payload) {
        JsonNode timezoneNode = payload.path("timezone");
        if (timezoneNode.isTextual()) {
            String timezone = timezoneNode.asText("");
            return timezone == null || timezone.isBlank() ? "UTC" : timezone;
        }

        if (timezoneNode.isObject()) {
            String id = text(timezoneNode, "id", "");
            if (id != null && !id.isBlank()) {
                return id;
            }

            String utc = text(timezoneNode, "utc", "");
            if (utc != null && !utc.isBlank()) {
                return utc;
            }
        }

        return "UTC";
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

    private String text(JsonNode payload, String primaryField, String secondaryField, String defaultValue) {
        String primaryValue = payload.path(primaryField).asText("");
        if (primaryValue != null && !primaryValue.isBlank()) {
            return primaryValue;
        }

        if (secondaryField == null || secondaryField.isBlank()) {
            return defaultValue;
        }

        String secondaryValue = payload.path(secondaryField).asText("");
        return secondaryValue == null || secondaryValue.isBlank() ? defaultValue : secondaryValue;
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