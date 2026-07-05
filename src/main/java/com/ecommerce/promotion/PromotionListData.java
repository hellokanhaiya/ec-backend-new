package com.ecommerce.promotion;

import java.util.List;

public record PromotionListData(List<PromotionData> items, long total, int page, int size) {}
