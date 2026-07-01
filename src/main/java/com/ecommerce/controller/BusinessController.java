package com.ecommerce.controller;

import com.ecommerce.auth.AuthAudience;
import com.ecommerce.auth.BusinessStoreData;
import com.ecommerce.auth.BusinessStoreRequest;
import com.ecommerce.auth.CurrentAccountData;
import com.ecommerce.auth.CurrentAccountService;
import com.ecommerce.store.BusinessStoreService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BusinessController {
    private final BusinessStoreService businessStoreService;
    private final CurrentAccountService currentAccountService;

    public BusinessController(BusinessStoreService businessStoreService, CurrentAccountService currentAccountService) {
        this.businessStoreService = businessStoreService;
        this.currentAccountService = currentAccountService;
    }

    @GetMapping("/public/business/categories")
    public ResponseEntity<Map<String, Object>> categories() {
        return ok("Business categories loaded", businessStoreService.listCategories());
    }

    @GetMapping("/{audience}/auth/business/store-profile")
    public ResponseEntity<Map<String, Object>> getStoreProfile(
            @PathVariable String audience,
            @RequestParam(required = false) String publicUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthAudience resolvedAudience = AuthAudience.from(audience);
        String resolvedPublicUserId = resolvePublicUserId(authorization, publicUserId, resolvedAudience);
        BusinessStoreData data = businessStoreService.getStoreProfile(resolvedAudience, resolvedPublicUserId);
        return ok("Store profile loaded", data);
    }

    @PostMapping("/{audience}/auth/business/store-profile")
    public ResponseEntity<Map<String, Object>> saveStoreProfile(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody BusinessStoreRequest request) {
        CurrentAccountData currentAccount = currentAccountService.resolveCurrentAccount(authorization);
        AuthAudience resolvedAudience = AuthAudience.from(audience);
        if (currentAccount.audience() != resolvedAudience) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Audience mismatch");
        }

        BusinessStoreData data = businessStoreService.saveStoreProfile(
                resolvedAudience,
                currentAccount.user().userId(),
                currentAccount.user().publicUserId(),
                request);
        return ok("Store profile saved", data);
    }

    private String resolvePublicUserId(String authorization, String requestedPublicUserId, AuthAudience audience) {
        if (requestedPublicUserId != null && !requestedPublicUserId.isBlank()) {
            return requestedPublicUserId.trim();
        }

        CurrentAccountData currentAccount = currentAccountService.resolveCurrentAccount(authorization);
        if (currentAccount.audience() != audience) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Audience mismatch");
        }

        return currentAccount.user().publicUserId();
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
