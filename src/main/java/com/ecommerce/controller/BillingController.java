package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.billing.BillingOverviewData;
import com.ecommerce.billing.CheckoutData;
import com.ecommerce.billing.CheckoutRequest;
import com.ecommerce.billing.EntitlementService;
import com.ecommerce.billing.EntitlementsData;
import com.ecommerce.billing.PlanData;
import com.ecommerce.billing.StoreBillingService;
import com.ecommerce.billing.SubscriptionData;
import com.ecommerce.billing.VerifyRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BillingController {
    private final StoreBillingService billingService;
    private final EntitlementService entitlementService;
    private final AccessControlService accessControl;

    public BillingController(
            StoreBillingService billingService,
            EntitlementService entitlementService,
            AccessControlService accessControl) {
        this.billingService = billingService;
        this.entitlementService = entitlementService;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/billing/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String storeId = resolve(authorization, audience, AccessLevel.VIEW);
        BillingOverviewData data = billingService.getOverview(storeId);
        return ok("Billing overview loaded", data);
    }

    @GetMapping("/{audience}/auth/billing/plans")
    public ResponseEntity<Map<String, Object>> plans(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        resolve(authorization, audience, AccessLevel.VIEW);
        List<PlanData> data = billingService.getPlans();
        return ok("Plans loaded", data);
    }

    @GetMapping("/{audience}/auth/billing/subscription")
    public ResponseEntity<Map<String, Object>> subscription(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String storeId = resolve(authorization, audience, AccessLevel.VIEW);
        SubscriptionData data = billingService.getSubscription(storeId);
        return ok("Subscription loaded", data);
    }

    @PostMapping("/{audience}/auth/billing/checkout")
    public ResponseEntity<Map<String, Object>> checkout(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CheckoutRequest request) {
        String storeId = resolve(authorization, audience, AccessLevel.MANAGE);
        CheckoutData data = billingService.createCheckout(storeId, request);
        return ok("Checkout created", data);
    }

    @PostMapping("/{audience}/auth/billing/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody VerifyRequest request) {
        String storeId = resolve(authorization, audience, AccessLevel.MANAGE);
        SubscriptionData data = billingService.verifyPayment(storeId, request);
        return ok("Payment verified", data);
    }

    // Readable by any authenticated store user (used to gate paid features on
    // pages like Bundles / Purchase Orders), so it requires no specific permission.
    @GetMapping("/{audience}/auth/billing/entitlements")
    public ResponseEntity<Map<String, Object>> entitlements(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = accessControl.requireScope(
                authorization, audience, PermissionCatalog.SETTINGS_BILLING, AccessLevel.NONE);
        EntitlementsData data = entitlementService.entitlements(scope.storeId());
        return ok("Entitlements loaded", data);
    }

    @PostMapping("/{audience}/auth/billing/cancel")
    public ResponseEntity<Map<String, Object>> cancel(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String storeId = resolve(authorization, audience, AccessLevel.MANAGE);
        SubscriptionData data = billingService.cancel(storeId);
        return ok("Subscription updated", data);
    }

    private String resolve(String authorization, String audience, AccessLevel required) {
        StoreAccessScope scope =
                accessControl.requireScope(authorization, audience, PermissionCatalog.SETTINGS_BILLING, required);
        return scope.storeId();
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
