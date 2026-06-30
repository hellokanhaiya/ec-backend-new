package com.ecommerce.controller;

import com.ecommerce.auth.AuthAudience;
import com.ecommerce.auth.BusinessStoreData;
import com.ecommerce.auth.BusinessStoreRequest;
import com.ecommerce.store.BusinessStoreService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BusinessController {
    private final BusinessStoreService businessStoreService;

    public BusinessController(BusinessStoreService businessStoreService) {
        this.businessStoreService = businessStoreService;
    }

    @GetMapping("/public/business/categories")
    public ResponseEntity<Map<String, Object>> categories() {
        return ok("Business categories loaded", businessStoreService.listCategories());
    }

    @GetMapping("/{audience}/auth/business/store-profile")
    public ResponseEntity<Map<String, Object>> getStoreProfile(
            @PathVariable String audience,
            @RequestParam String publicUserId) {
        BusinessStoreData data = businessStoreService.getStoreProfile(AuthAudience.from(audience), publicUserId);
        return ok("Store profile loaded", data);
    }

    @PostMapping("/{audience}/auth/business/store-profile")
    public ResponseEntity<Map<String, Object>> saveStoreProfile(
            @PathVariable String audience,
            @RequestBody BusinessStoreRequest request) {
        BusinessStoreData data = businessStoreService.saveStoreProfile(AuthAudience.from(audience), request);
        return ok("Store profile saved", data);
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
