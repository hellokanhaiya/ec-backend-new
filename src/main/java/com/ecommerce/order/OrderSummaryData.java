package com.ecommerce.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderSummaryData(
        String id,
        String orderNumber,
        String status,
        String customer,
        String email,
        String phone,
        Instant date,
        Instant updatedAt,
        BigDecimal total,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal discount,
        String promotionPublicId,
        String promotionCode,
        String promotionName,
        String promotionType,
        String promotionSummary,
        boolean promotionFreeShipping,
        BigDecimal promotionShippingSavings,
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
        String notes,
        OrderAddressData address,
        String currencyCode,
        String currencySymbol,
        String currencyCountryCode,
        String warehousePublicId) {}
