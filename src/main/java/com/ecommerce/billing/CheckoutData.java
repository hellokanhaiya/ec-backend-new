package com.ecommerce.billing;

import java.math.BigDecimal;

/**
 * Handed to the frontend to open the Razorpay checkout. The price breakdown is
 * computed by the backend (authoritative): {@code totalAmount = baseAmount +
 * taxAmount}, and {@code amount} is that total in paise — exactly what the
 * Razorpay order was created for, so the modal and the charge always match.
 */
public record CheckoutData(
        String razorpayOrderId,
        String keyId,
        long amount, // total in the smallest currency unit (paise)
        String currencyCode,
        String planCode,
        String planName,
        String billingCycle,
        BigDecimal baseAmount,
        BigDecimal taxPercent,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        boolean testMode) {}
