package com.ecommerce.shipping;

import java.util.List;

public record ShippingZoneRequest(
        String profilePublicId, String name, String currencyCode, List<ShippingRegionRequest> regions) {}
