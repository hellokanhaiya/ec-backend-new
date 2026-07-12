package com.ecommerce.controller;

import com.ecommerce.analytics.AnalyticsService;
import com.ecommerce.billing.EntitlementService;
import com.ecommerce.billing.StoreBillingService;
import com.ecommerce.plugin.PluginAccessControlService;
import com.ecommerce.plugin.PluginAccessScope;
import com.ecommerce.plugin.PluginApiResponses;
import com.ecommerce.plugin.PluginScopeCatalog;
import com.ecommerce.tax.TaxService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only plugin API for insights: analytics/dashboard, billing, and taxes. Plugin-token auth
 * only; no write handlers.
 */
@RestController
@RequestMapping("/api/v1/plugin")
public class PluginInsightsApiController {
    private final AnalyticsService analyticsService;
    private final StoreBillingService billingService;
    private final EntitlementService entitlementService;
    private final TaxService taxService;
    private final PluginAccessControlService pluginAccessControl;

    public PluginInsightsApiController(
            AnalyticsService analyticsService,
            StoreBillingService billingService,
            EntitlementService entitlementService,
            TaxService taxService,
            PluginAccessControlService pluginAccessControl) {
        this.analyticsService = analyticsService;
        this.billingService = billingService;
        this.entitlementService = entitlementService;
        this.taxService = taxService;
        this.pluginAccessControl = pluginAccessControl;
    }

    // --- Dashboard ----------------------------------------------------------

    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> dashboardStats(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope = analytics(authorization);
        return PluginApiResponses.ok("Dashboard stats loaded", analyticsService.dashboardStats(scope.storeId()));
    }

    @GetMapping("/dashboard/revenue-trend")
    public ResponseEntity<Map<String, Object>> dashboardRevenueTrend(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope = analytics(authorization);
        return PluginApiResponses.ok(
                "Dashboard revenue trend loaded", analyticsService.dashboardRevenueTrend(scope.storeId()));
    }

    @GetMapping("/dashboard/traffic")
    public ResponseEntity<Map<String, Object>> dashboardTraffic(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope = analytics(authorization);
        return PluginApiResponses.ok("Dashboard traffic loaded", analyticsService.dashboardTraffic(scope.storeId()));
    }

    @GetMapping("/dashboard/recent-orders")
    public ResponseEntity<Map<String, Object>> dashboardRecentOrders(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope = analytics(authorization);
        return PluginApiResponses.ok(
                "Recent orders loaded", analyticsService.dashboardRecentOrders(scope.storeId()));
    }

    @GetMapping("/dashboard/top-products")
    public ResponseEntity<Map<String, Object>> dashboardTopProducts(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope = analytics(authorization);
        return PluginApiResponses.ok(
                "Top products loaded", analyticsService.dashboardTopProducts(scope.storeId()));
    }

    @GetMapping("/dashboard/low-stock")
    public ResponseEntity<Map<String, Object>> dashboardLowStock(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope = analytics(authorization);
        return PluginApiResponses.ok("Low stock loaded", analyticsService.dashboardLowStock(scope.storeId()));
    }

    // --- Analytics ----------------------------------------------------------

    @GetMapping("/analytics/kpis")
    public ResponseEntity<Map<String, Object>> analyticsKpis(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "thisMonth") String dateRange) {
        PluginAccessScope scope = analytics(authorization);
        return PluginApiResponses.ok("Analytics KPIs loaded", analyticsService.analyticsKpis(scope.storeId(), dateRange));
    }

    @GetMapping("/analytics/revenue-trend")
    public ResponseEntity<Map<String, Object>> analyticsRevenueTrend(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "thisMonth") String dateRange) {
        PluginAccessScope scope = analytics(authorization);
        return PluginApiResponses.ok(
                "Analytics revenue trend loaded", analyticsService.analyticsRevenueTrend(scope.storeId(), dateRange));
    }

    @GetMapping("/analytics/sales-breakdown")
    public ResponseEntity<Map<String, Object>> analyticsSalesBreakdown(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "thisMonth") String dateRange) {
        PluginAccessScope scope = analytics(authorization);
        return PluginApiResponses.ok(
                "Analytics sales breakdown loaded", analyticsService.analyticsSalesBreakdown(scope.storeId(), dateRange));
    }

    @GetMapping("/analytics/traffic")
    public ResponseEntity<Map<String, Object>> analyticsTraffic(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "thisMonth") String dateRange) {
        PluginAccessScope scope = analytics(authorization);
        return PluginApiResponses.ok(
                "Analytics traffic loaded", analyticsService.analyticsTraffic(scope.storeId(), dateRange));
    }

    // --- Billing ------------------------------------------------------------

    @GetMapping("/billing/overview")
    public ResponseEntity<Map<String, Object>> billingOverview(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.BILLING_READ);
        return PluginApiResponses.ok("Billing overview loaded", billingService.getOverview(scope.storeId()));
    }

    @GetMapping("/billing/plans")
    public ResponseEntity<Map<String, Object>> billingPlans(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.BILLING_READ);
        return PluginApiResponses.ok("Plans loaded", billingService.getPlans());
    }

    @GetMapping("/billing/subscription")
    public ResponseEntity<Map<String, Object>> billingSubscription(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.BILLING_READ);
        return PluginApiResponses.ok("Subscription loaded", billingService.getSubscription(scope.storeId()));
    }

    @GetMapping("/billing/entitlements")
    public ResponseEntity<Map<String, Object>> billingEntitlements(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.BILLING_READ);
        return PluginApiResponses.ok("Entitlements loaded", entitlementService.entitlements(scope.storeId()));
    }

    // --- Taxes --------------------------------------------------------------

    @GetMapping("/tax/rates")
    public ResponseEntity<Map<String, Object>> taxRates(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String country) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.TAX_READ);
        String countryCode = (country == null || country.isBlank()) ? scope.countryCode() : country;
        return PluginApiResponses.ok("Tax rates loaded", taxService.rates(countryCode));
    }

    private PluginAccessScope analytics(String authorization) {
        return pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.ANALYTICS_READ);
    }
}
