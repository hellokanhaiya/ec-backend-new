package com.ecommerce.controller;

import com.ecommerce.abandoned.AbandonedCartData;
import com.ecommerce.abandoned.AbandonedCartListData;
import com.ecommerce.abandoned.AbandonedCartOverviewData;
import com.ecommerce.abandoned.AbandonedCartService;
import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AbandonedCartController {

    private final AbandonedCartService service;
    private final AccessControlService accessControl;

    public AbandonedCartController(AbandonedCartService service, AccessControlService accessControl) {
        this.service = service;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/abandoned-carts")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.VIEW);
        AbandonedCartListData data =
                service.list(scope.storeId(), scope.ownerPublicUserId(), search, status, page, size);
        return ok("Abandoned carts loaded", data);
    }

    @GetMapping("/{audience}/auth/abandoned-carts/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.VIEW);
        AbandonedCartOverviewData data = service.overview(scope.storeId(), scope.ownerPublicUserId());
        return ok("Abandoned cart overview loaded", data);
    }

    @GetMapping("/{audience}/auth/abandoned-carts/{publicCartId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String audience,
            @PathVariable String publicCartId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.VIEW);
        AbandonedCartData data = service.get(scope.storeId(), publicCartId);
        return ok("Abandoned cart loaded", data);
    }

    @PostMapping("/{audience}/auth/abandoned-carts/{publicCartId}/recover")
    public ResponseEntity<Map<String, Object>> recover(
            @PathVariable String audience,
            @PathVariable String publicCartId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.EDIT);
        AbandonedCartData data = service.sendRecoveryEmail(scope.storeId(), publicCartId);
        return ok("Recovery reminder sent", data);
    }

    // --- Helpers ------------------------------------------------------------

    private StoreScope resolveScope(String authorization, String audience, AccessLevel required) {
        StoreAccessScope scope =
                accessControl.requireScope(
                        authorization, audience, PermissionCatalog.ORDERS_ABANDONED, required);
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
