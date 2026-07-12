package com.ecommerce.analytics;

import java.util.List;

public record AnalyticsPageData(
        KpiData customers,
        KpiData orders,
        KpiData aov,
        KpiData abandonedCarts,
        List<RevenueTrendItem> revenueTrend,
        List<SalesBreakdownItem> salesBreakdown,
        List<TrafficSourceItem> trafficData) {}
