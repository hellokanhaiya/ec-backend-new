package com.ecommerce.abandoned;

import java.util.List;

public record AbandonedCartListData(List<AbandonedCartData> items, long total, int page, int size) {}
