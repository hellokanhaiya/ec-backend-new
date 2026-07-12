package com.ecommerce.order;

import java.math.BigDecimal;
import java.util.List;

public record OrderRequest(
        String customerPublicId,
        String warehousePublicId,
        InlineCustomerRequest newCustomer,
        String customerName,
        String email,
        String phone,
        String payment,
        String fulfillment,
        String status,
        String channel,
        String courier,
        String trackingNumber,
        String notes,
        OrderAddressRequest address,
        OrderDiscountRequest discount,
        BigDecimal shippingCharge,
        String shippingLabel,
        BigDecimal packageCharge,
        String currencyCode,
        String currencySymbol,
        String currencyCountryCode,
        List<String> tags,
        List<OrderLineItemRequest> products) {}
