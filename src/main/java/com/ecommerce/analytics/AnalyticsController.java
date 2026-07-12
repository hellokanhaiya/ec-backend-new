package com.ecommerce.analytics;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AccessControlService accessControl;

    public AnalyticsController(AnalyticsService analyticsService, AccessControlService accessControl) {
        this.analyticsService = analyticsService;
        this.accessControl = accessControl;
    }

    // ─── Dashboard Endpoints (individual) ──────────────────────────────────────

    @GetMapping("/{audience}/auth/dashboard/stats")
    public ResponseEntity<Map<String, Object>> dashboardStats(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = requireScope(authorization, audience);
        return ok("Dashboard stats loaded", analyticsService.dashboardStats(scope.storeId()));
    }

    @GetMapping("/{audience}/auth/dashboard/revenue-trend")
    public ResponseEntity<Map<String, Object>> dashboardRevenueTrend(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = requireScope(authorization, audience);
        return ok("Revenue trend loaded", analyticsService.dashboardRevenueTrend(scope.storeId()));
    }

    @GetMapping("/{audience}/auth/dashboard/traffic")
    public ResponseEntity<Map<String, Object>> dashboardTraffic(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = requireScope(authorization, audience);
        return ok("Traffic data loaded", analyticsService.dashboardTraffic(scope.storeId()));
    }

    @GetMapping("/{audience}/auth/dashboard/recent-orders")
    public ResponseEntity<Map<String, Object>> dashboardRecentOrders(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = requireScope(authorization, audience);
        return ok("Recent orders loaded", analyticsService.dashboardRecentOrders(scope.storeId()));
    }

    @GetMapping("/{audience}/auth/dashboard/top-products")
    public ResponseEntity<Map<String, Object>> dashboardTopProducts(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = requireScope(authorization, audience);
        return ok("Top products loaded", analyticsService.dashboardTopProducts(scope.storeId()));
    }

    @GetMapping("/{audience}/auth/dashboard/low-stock")
    public ResponseEntity<Map<String, Object>> dashboardLowStock(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = requireScope(authorization, audience);
        return ok("Low stock products loaded", analyticsService.dashboardLowStock(scope.storeId()));
    }

    // ─── Analytics Endpoints (individual) ──────────────────────────────────────

    @GetMapping("/{audience}/auth/analytics/kpis")
    public ResponseEntity<Map<String, Object>> analyticsKpis(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false, defaultValue = "thisMonth") String dateRange) {
        StoreAccessScope scope = requireScope(authorization, audience);
        return ok("Analytics KPIs loaded", analyticsService.analyticsKpis(scope.storeId(), dateRange));
    }

    @GetMapping("/{audience}/auth/analytics/revenue-trend")
    public ResponseEntity<Map<String, Object>> analyticsRevenueTrend(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false, defaultValue = "thisMonth") String dateRange) {
        StoreAccessScope scope = requireScope(authorization, audience);
        return ok("Analytics revenue trend loaded", analyticsService.analyticsRevenueTrend(scope.storeId(), dateRange));
    }

    @GetMapping("/{audience}/auth/analytics/sales-breakdown")
    public ResponseEntity<Map<String, Object>> analyticsSalesBreakdown(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false, defaultValue = "thisMonth") String dateRange) {
        StoreAccessScope scope = requireScope(authorization, audience);
        return ok("Analytics sales breakdown loaded", analyticsService.analyticsSalesBreakdown(scope.storeId(), dateRange));
    }

    @GetMapping("/{audience}/auth/analytics/traffic")
    public ResponseEntity<Map<String, Object>> analyticsTraffic(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false, defaultValue = "thisMonth") String dateRange) {
        StoreAccessScope scope = requireScope(authorization, audience);
        return ok("Analytics traffic loaded", analyticsService.analyticsTraffic(scope.storeId(), dateRange));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private StoreAccessScope requireScope(String authorization, String audience) {
        return accessControl.requireScope(
                authorization, audience, PermissionCatalog.ANALYTICS_DASHBOARD, AccessLevel.VIEW);
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
