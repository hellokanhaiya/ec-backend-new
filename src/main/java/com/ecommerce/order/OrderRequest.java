package com.ecommerce.order;

import java.math.BigDecimal;
import java.util.List;

public record OrderRequest(
        String customerPublicId,
        InlineCustomerRequest newCustomer,
        String customerName,
        String email,
        String phone,
        String payment,
        String fulfillment,
        String channel,
        String courier,
        String trackingNumber,
        String notes,
        OrderAddressRequest address,
        OrderDiscountRequest discount,
        BigDecimal shippingCharge,
        BigDecimal packageCharge,
        String currencyCode,
        String currencySymbol,
        String currencyCountryCode,
        List<String> tags,
        List<OrderLineItemRequest> products) {}
