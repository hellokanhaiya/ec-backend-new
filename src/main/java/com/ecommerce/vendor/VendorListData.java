package com.ecommerce.vendor;

import java.util.List;

public record VendorListData(List<VendorData> items, long total, int page, int size) {}
