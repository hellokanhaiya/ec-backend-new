package com.ecommerce.billing;

import java.math.BigDecimal;
import java.time.Instant;

public record BillingTransactionData(
        String planCode,
        String billingCycle,
        BigDecimal amount,
        String currencyCode,
        String status,
        String razorpayOrderId,
        String razorpayPaymentId,
        Instant createdAt,
        Instant paidAt) {}
