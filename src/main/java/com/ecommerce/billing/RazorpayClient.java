package com.ecommerce.billing;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Thin Razorpay Orders API client using the JDK HTTP client (no extra SDK
 * dependency). Handles order creation and the standard checkout signature
 * verification (HMAC-SHA256 of "order_id|payment_id" with the key secret).
 * See https://razorpay.com/docs/api/orders and .../payments/payment-gateway/web-integration.
 */
@Component
public class RazorpayClient {
    private static final String ORDERS_URL = "https://api.razorpay.com/v1/orders";

    private final String keyId;
    private final String keySecret;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public RazorpayClient(
            @Value("${razorpay.key-id:}") String keyId,
            @Value("${razorpay.key-secret:}") String keySecret,
            ObjectMapper objectMapper) {
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return !keyId.isBlank() && !keySecret.isBlank();
    }

    public boolean isTestMode() {
        return keyId.startsWith("rzp_test");
    }

    public String keyId() {
        return keyId;
    }

    /** Create a Razorpay order and return its id (e.g. {@code order_XXXXXXXX}). */
    public String createOrder(long amountInPaise, String currency, String receipt, Map<String, String> notes) {
        requireConfigured();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("amount", amountInPaise);
            body.put("currency", currency == null ? "INR" : currency);
            body.put("receipt", receipt);
            body.put("payment_capture", 1);
            if (notes != null && !notes.isEmpty()) {
                body.put("notes", notes);
            }
            String payload = objectMapper.writeValueAsString(body);
            String auth = Base64.getEncoder()
                    .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(ORDERS_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (response.statusCode() >= 200 && response.statusCode() < 300 && json.hasNonNull("id")) {
                return json.get("id").asText();
            }
            String message = json.path("error").path("description").asText("Razorpay order creation failed");
            throw new ResponseStatusException(BAD_GATEWAY, "Razorpay: " + message);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Could not reach Razorpay: " + ex.getMessage());
        }
    }

    /** Verify the checkout callback signature. */
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        requireConfigured();
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }
        String expected = hmacSha256Hex(orderId + "|" + paymentId, keySecret);
        return constantTimeEquals(expected, signature.trim());
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(
                    SERVICE_UNAVAILABLE,
                    "Razorpay keys are not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET in the backend .env.");
        }
    }

    private static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute HMAC", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
