package com.ecommerce.promotion;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PromotionController {
    private final StorePromotionService promotionService;
    private final AccessControlService accessControl;

    public PromotionController(StorePromotionService promotionService, AccessControlService accessControl) {
        this.promotionService = promotionService;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/promotions")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "0") int size) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.VIEW);
        PromotionListData data = promotionService.list(scope.storeId(), search, type, status, page, size);
        return ok("Promotions loaded", data);
    }

    @GetMapping("/{audience}/auth/promotions/{publicPromotionId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String audience,
            @PathVariable String publicPromotionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.VIEW);
        return ok("Promotion loaded", promotionService.get(scope.storeId(), publicPromotionId));
    }

    @PostMapping("/{audience}/auth/promotions")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PromotionRequest request) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.MANAGE);
        return ok("Promotion created", promotionService.create(scope.storeId(), scope.ownerPublicUserId(), request));
    }

    @PutMapping("/{audience}/auth/promotions/{publicPromotionId}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String audience,
            @PathVariable String publicPromotionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PromotionRequest request) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.EDIT);
        return ok("Promotion updated", promotionService.update(scope.storeId(), publicPromotionId, request));
    }

    @PostMapping("/{audience}/auth/promotions/{publicPromotionId}/activate")
    public ResponseEntity<Map<String, Object>> activate(
            @PathVariable String audience,
            @PathVariable String publicPromotionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.EDIT);
        return ok("Promotion activated", promotionService.activate(scope.storeId(), publicPromotionId));
    }

    @PostMapping("/{audience}/auth/promotions/{publicPromotionId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivate(
            @PathVariable String audience,
            @PathVariable String publicPromotionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.EDIT);
        return ok("Promotion deactivated", promotionService.deactivate(scope.storeId(), publicPromotionId));
    }

    @DeleteMapping("/{audience}/auth/promotions/{publicPromotionId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String audience,
            @PathVariable String publicPromotionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.MANAGE);
        promotionService.delete(scope.storeId(), publicPromotionId);
        return ok("Promotion deleted", null);
    }

    @PostMapping("/{audience}/auth/promotions/resolve")
    public ResponseEntity<Map<String, Object>> resolve(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PromotionApplyRequest request) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.VIEW);
        return ok("Promotion resolved", promotionService.resolve(scope.storeId(), request));
    }

    private StoreScope resolveScope(String authorization, String audience, AccessLevel required) {
        StoreAccessScope scope = accessControl.requireScope(authorization, audience, PermissionCatalog.MARKETING_DISCOUNTS, required);
        return new StoreScope(scope.storeId(), scope.publicUserId());
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    private record StoreScope(String storeId, String ownerPublicUserId) {}
}
