package com.ecommerce.controller;

import com.ecommerce.auth.AuthAudience;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private static final String LOOKUP_REQUEST_CLASS = "com.ecommerce.auth.AuthLookupRequest";
    private static final String OTP_REQUEST_CLASS = "com.ecommerce.auth.OtpRequestPayload";
    private static final String OTP_VERIFY_CLASS = "com.ecommerce.auth.OtpVerifyPayload";

    private final Object authService;
    private final ObjectMapper objectMapper;

    public AuthController(com.ecommerce.auth.AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{audience}/auth/account/lookup")
    public ResponseEntity<Map<String, Object>> lookup(
            @PathVariable String audience,
            @RequestBody JsonNode request) {
        return ok("Account lookup complete", invokeLookup(AuthAudience.from(audience), request));
    }

    @PostMapping("/auth/oauth/otp-customer/lookup")
    public ResponseEntity<Map<String, Object>> lookupConsumer(@RequestBody JsonNode request) {
        return ok("Account lookup complete", invokeLookup(AuthAudience.CONSUMER, request));
    }

    @PostMapping("/auth/oauth/v2/otp/lookup")
    public ResponseEntity<Map<String, Object>> lookupAdmin(@RequestBody JsonNode request) {
        return ok("Account lookup complete", invokeLookup(AuthAudience.ADMIN, request));
    }

    @PostMapping("/{audience}/auth/otp/request")
    public ResponseEntity<Map<String, Object>> requestOtp(
            @PathVariable String audience,
            @RequestBody JsonNode request) {
        return ok("OTP request created", invokeRequestOtp(AuthAudience.from(audience), request));
    }

    @PostMapping("/auth/oauth/otp-customer/request")
    public ResponseEntity<Map<String, Object>> requestOtpConsumer(@RequestBody JsonNode request) {
        return ok("OTP request created", invokeRequestOtp(AuthAudience.CONSUMER, request));
    }

    @PostMapping("/auth/oauth/v2/otp/request")
    public ResponseEntity<Map<String, Object>> requestOtpAdmin(@RequestBody JsonNode request) {
        return ok("OTP request created", invokeRequestOtp(AuthAudience.ADMIN, request));
    }

    @PostMapping("/{audience}/auth/otp/verify")
    public ResponseEntity<Map<String, Object>> verifyOtp(
            @PathVariable String audience,
            @RequestBody JsonNode request) {
        return ok("OTP verified", invokeVerifyOtp(AuthAudience.from(audience), request));
    }

    @PostMapping("/auth/oauth/otp-customer/verify")
    public ResponseEntity<Map<String, Object>> verifyOtpConsumer(@RequestBody JsonNode request) {
        return ok("OTP verified", invokeVerifyOtp(AuthAudience.CONSUMER, request));
    }

    @PostMapping("/auth/oauth/v2/otp/verify")
    public ResponseEntity<Map<String, Object>> verifyOtpAdmin(@RequestBody JsonNode request) {
        return ok("OTP verified", invokeVerifyOtp(AuthAudience.ADMIN, request));
    }

    @PostMapping("/{audience}/auth/oauth/{provider}/start")
    public ResponseEntity<Map<String, Object>> startOAuth(
            @PathVariable String audience,
            @PathVariable String provider) {
        return ok("OAuth start ready", invoke("startOAuth", new Class<?>[] {AuthAudience.class, String.class}, AuthAudience.from(audience), provider));
    }

    @PostMapping("/{audience}/auth/oauth/{provider}/callback")
    public ResponseEntity<Map<String, Object>> oauthCallback(
            @PathVariable String audience,
            @PathVariable String provider,
            @RequestBody JsonNode request) {
        Object payload = objectMapper.convertValue(request, resolveClass("com.ecommerce.auth.OAuthCallbackPayload"));
        return ok("OAuth callback handled", invoke("callbackOAuth", new Class<?>[] {AuthAudience.class, String.class, payload.getClass()}, AuthAudience.from(audience), provider, payload));
    }

    private Object invokeLookup(AuthAudience audience, JsonNode request) {
        Object payload = objectMapper.convertValue(request, resolveClass(LOOKUP_REQUEST_CLASS));
        return invoke("lookup", new Class<?>[] {AuthAudience.class, payload.getClass()}, audience, payload);
    }

    private Object invokeRequestOtp(AuthAudience audience, JsonNode request) {
        Object payload = objectMapper.convertValue(request, resolveClass(OTP_REQUEST_CLASS));
        return invoke("requestOtp", new Class<?>[] {AuthAudience.class, payload.getClass()}, audience, payload);
    }

    private Object invokeVerifyOtp(AuthAudience audience, JsonNode request) {
        Object payload = objectMapper.convertValue(request, resolveClass(OTP_VERIFY_CLASS));
        return invoke("verifyOtp", new Class<?>[] {AuthAudience.class, payload.getClass()}, audience, payload);
    }

    private Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = authService.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(authService, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to call auth service method " + methodName, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Class<T> resolveClass(String className) {
        try {
            return (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Missing auth DTO class: " + className, exception);
        }
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
