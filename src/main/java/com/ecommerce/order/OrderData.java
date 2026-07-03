package com.ecommerce.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderData(
        String id,
        String orderNumber,
        String customer,
        String customerPublicId,
        String email,
        String phone,
        Instant date,
        Instant updatedAt,
        BigDecimal total,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal discount,
        BigDecimal shippingCharge,
        BigDecimal packageCharge,
        String payment,
        String paymentStatus,
        String fulfillment,
        String fulfillmentStatus,
        int items,
        List<OrderLineItemData> products,
        List<String> tags,
        String channel,
        String courier,
        String trackingNumber,
        List<OrderTimelineData> timeline,
        String notes,
        OrderAddressData address) {}
