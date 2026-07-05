package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.shipping.DeliverySettingsRequest;
import com.ecommerce.shipping.PincodeRange;
import com.ecommerce.shipping.PincodeRangeRequest;
import com.ecommerce.shipping.ShippingProfileRequest;
import com.ecommerce.shipping.ShippingQuickSetupRequest;
import com.ecommerce.shipping.ShippingQuoteRequest;
import com.ecommerce.shipping.ShippingRateRequest;
import com.ecommerce.shipping.ShippingZoneRequest;
import com.ecommerce.shipping.StoreShippingService;
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
public class ShippingController {
    private final StoreShippingService shippingService;
    private final AccessControlService accessControl;

    public ShippingController(StoreShippingService shippingService, AccessControlService accessControl) {
        this.shippingService = shippingService;
        this.accessControl = accessControl;
    }

    // --- settings tree -----------------------------------------------------

    @GetMapping("/{audience}/auth/shipping/settings")
    public ResponseEntity<Map<String, Object>> settings(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.VIEW);
        return ok("Shipping settings loaded", shippingService.settings(scope.storeId(), scope.ownerPublicUserId()));
    }

    @PostMapping("/{audience}/auth/shipping/quick-setup")
    public ResponseEntity<Map<String, Object>> quickSetup(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ShippingQuickSetupRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.MANAGE);
        return ok(
                "Delivery setup saved",
                shippingService.applyQuickSetup(scope.storeId(), scope.ownerPublicUserId(), request));
    }

    // --- delivery settings (dispatch time, COD, free-shipping threshold) ----

    @GetMapping("/{audience}/auth/shipping/delivery-settings")
    public ResponseEntity<Map<String, Object>> getDeliverySettings(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.VIEW);
        return ok("Delivery settings loaded", shippingService.getDeliverySettings(scope.storeId()));
    }

    @PutMapping("/{audience}/auth/shipping/delivery-settings")
    public ResponseEntity<Map<String, Object>> saveDeliverySettings(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody DeliverySettingsRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.EDIT);
        return ok("Delivery settings saved", shippingService.saveDeliverySettings(scope.storeId(), request));
    }

    // --- profiles ----------------------------------------------------------

    @PostMapping("/{audience}/auth/shipping/profiles")
    public ResponseEntity<Map<String, Object>> createProfile(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ShippingProfileRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.MANAGE);
        return ok("Profile created", shippingService.createProfile(scope.storeId(), scope.ownerPublicUserId(), request));
    }

    @PutMapping("/{audience}/auth/shipping/profiles/{publicProfileId}")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @PathVariable String audience,
            @PathVariable String publicProfileId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ShippingProfileRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.EDIT);
        return ok("Profile updated", shippingService.updateProfile(scope.storeId(), publicProfileId, request));
    }

    @DeleteMapping("/{audience}/auth/shipping/profiles/{publicProfileId}")
    public ResponseEntity<Map<String, Object>> deleteProfile(
            @PathVariable String audience,
            @PathVariable String publicProfileId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.MANAGE);
        shippingService.deleteProfile(scope.storeId(), publicProfileId);
        return ok("Profile deleted", null);
    }

    // --- zones -------------------------------------------------------------

    @PostMapping("/{audience}/auth/shipping/zones")
    public ResponseEntity<Map<String, Object>> createZone(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ShippingZoneRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.MANAGE);
        return ok("Zone created", shippingService.createZone(scope.storeId(), scope.ownerPublicUserId(), request));
    }

    @PutMapping("/{audience}/auth/shipping/zones/{publicZoneId}")
    public ResponseEntity<Map<String, Object>> updateZone(
            @PathVariable String audience,
            @PathVariable String publicZoneId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ShippingZoneRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.EDIT);
        return ok("Zone updated", shippingService.updateZone(scope.storeId(), publicZoneId, request));
    }

    @DeleteMapping("/{audience}/auth/shipping/zones/{publicZoneId}")
    public ResponseEntity<Map<String, Object>> deleteZone(
            @PathVariable String audience,
            @PathVariable String publicZoneId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.MANAGE);
        shippingService.deleteZone(scope.storeId(), publicZoneId);
        return ok("Zone deleted", null);
    }

    // --- rates -------------------------------------------------------------

    @PostMapping("/{audience}/auth/shipping/rates")
    public ResponseEntity<Map<String, Object>> createRate(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ShippingRateRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.MANAGE);
        return ok("Rate created", shippingService.createRate(scope.storeId(), scope.ownerPublicUserId(), request));
    }

    @PutMapping("/{audience}/auth/shipping/rates/{publicRateId}")
    public ResponseEntity<Map<String, Object>> updateRate(
            @PathVariable String audience,
            @PathVariable String publicRateId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ShippingRateRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.EDIT);
        return ok("Rate updated", shippingService.updateRate(scope.storeId(), publicRateId, request));
    }

    @DeleteMapping("/{audience}/auth/shipping/rates/{publicRateId}")
    public ResponseEntity<Map<String, Object>> deleteRate(
            @PathVariable String audience,
            @PathVariable String publicRateId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.MANAGE);
        shippingService.deleteRate(scope.storeId(), publicRateId);
        return ok("Rate deleted", null);
    }

    // --- pincode serviceability --------------------------------------------

    @GetMapping("/{audience}/auth/shipping/serviceability")
    public ResponseEntity<Map<String, Object>> listPincodes(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String zone) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.VIEW);
        return ok(
                "Serviceability loaded",
                shippingService.listPincodeRanges(scope.storeId(), scope.ownerPublicUserId(), zone));
    }

    @PostMapping("/{audience}/auth/shipping/serviceability")
    public ResponseEntity<Map<String, Object>> createPincode(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PincodeRangeRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.MANAGE);
        return ok(
                "Pincode range created",
                shippingService.createPincodeRange(scope.storeId(), scope.ownerPublicUserId(), request));
    }

    @PutMapping("/{audience}/auth/shipping/serviceability/{publicPincodeId}")
    public ResponseEntity<Map<String, Object>> updatePincode(
            @PathVariable String audience,
            @PathVariable String publicPincodeId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PincodeRangeRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.EDIT);
        return ok("Pincode range updated", shippingService.updatePincodeRange(scope.storeId(), publicPincodeId, request));
    }

    @DeleteMapping("/{audience}/auth/shipping/serviceability/{publicPincodeId}")
    public ResponseEntity<Map<String, Object>> deletePincode(
            @PathVariable String audience,
            @PathVariable String publicPincodeId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.MANAGE);
        shippingService.deletePincodeRange(scope.storeId(), publicPincodeId);
        return ok("Pincode range deleted", null);
    }

    @GetMapping("/{audience}/auth/shipping/serviceability/check")
    public ResponseEntity<Map<String, Object>> checkPincode(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String pincode) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.VIEW);
        PincodeRange match = shippingService.matchPincode(scope.storeId(), pincode).orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serviceable", match != null);
        data.put("zonePublicId", match == null ? null : match.getZonePublicId());
        data.put("codAvailable", match != null && match.isCodAvailable());
        data.put("etaMinDays", match == null ? null : match.getEtaMinDays());
        data.put("etaMaxDays", match == null ? null : match.getEtaMaxDays());
        return ok("Serviceability checked", data);
    }

    // --- quote -------------------------------------------------------------

    @PostMapping("/{audience}/auth/shipping/quote")
    public ResponseEntity<Map<String, Object>> quote(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ShippingQuoteRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.SHIPPING_SETTINGS, AccessLevel.VIEW);
        return ok("Quote ready", shippingService.quote(scope.storeId(), scope.ownerPublicUserId(), request));
    }

    // --- scope helpers -----------------------------------------------------

    private StoreScope resolveScope(String authorization, String audience, String permissionKey, AccessLevel required) {
        StoreAccessScope scope = accessControl.requireScope(authorization, audience, permissionKey, required);
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
