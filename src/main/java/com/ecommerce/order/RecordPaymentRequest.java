package com.ecommerce.order;

import java.math.BigDecimal;

/**
 * Records a payment taken against an order. {@code status} defaults to "paid";
 * {@code amount} defaults to the order total when omitted.
 */
public record RecordPaymentRequest(
        String status,
        String method,
        String reference,
        BigDecimal amount) {}
