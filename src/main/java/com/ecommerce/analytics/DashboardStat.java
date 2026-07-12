package com.ecommerce.analytics;

public record DashboardStat(
        String label,
        String value,
        String change,
        String changeText,
        String changeDir,
        String iconColor) {}
