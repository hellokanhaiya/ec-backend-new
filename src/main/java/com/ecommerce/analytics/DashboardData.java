package com.ecommerce.analytics;

import java.util.List;

public record DashboardData(
        List<DashboardStat> stats,
        List<RevenueTrendItem> revenueData,
        List<TrafficSourceItem> trafficData,
        long totalRevenue,
        List<RecentOrderData> recentOrders,
        List<TopProductData> topProducts,
        List<LowStockProduct> lowStockProducts) {}
