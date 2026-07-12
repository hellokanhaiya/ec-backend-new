package com.ecommerce.billing;

import java.time.LocalDate;

public record SubscriptionData(
        String status,
        String planCode,
        String planName,
        String billingCycle,
        LocalDate startedAt,
        LocalDate expiresAt,
        Integer daysRemaining,
        boolean expired,
        int creditsRemaining,
        boolean autoRenew,
        String lastPaymentId) {}
