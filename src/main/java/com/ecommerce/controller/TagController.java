package com.ecommerce.controller;

import com.ecommerce.auth.AuthAudience;
import com.ecommerce.auth.CurrentAccountData;
import com.ecommerce.auth.CurrentAccountService;
import com.ecommerce.tag.StoreTagService;
import com.ecommerce.tag.TagData;
import com.ecommerce.tag.TagRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Store-scoped tag library — the common/shared tag store. Tags created here (or
 * while editing a customer) are reusable across the store.
 */
@RestController
@RequestMapping("/api/v1")
public class TagController {
    private final StoreTagService tagService;
    private final CurrentAccountService currentAccountService;

    public TagController(StoreTagService tagService, CurrentAccountService currentAccountService) {
        this.tagService = tagService;
        this.currentAccountService = currentAccountService;
    }

    @GetMapping("/{audience}/auth/tags")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String storeId = resolveStoreId(authorization, audience);
        List<TagData> data = tagService.list(storeId);
        return ok("Tags loaded", data);
    }

    @PostMapping("/{audience}/auth/tags")
    public ResponseEntity<Map<String, Object>> upsert(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody TagRequest request) {
        String storeId = resolveStoreId(authorization, audience);
        TagData data = tagService.upsert(storeId, request == null ? null : request.name());
        return ok("Tag saved", data);
    }

    private String resolveStoreId(String authorization, String audience) {
        CurrentAccountData currentAccount = currentAccountService.resolveCurrentAccount(authorization);
        if (currentAccount.audience() != AuthAudience.from(audience)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audience mismatch");
        }
        if (currentAccount.store() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store setup required before managing tags");
        }
        return currentAccount.store().storeId();
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
