package com.ecommerce.purchase;

import java.util.List;

public record PurchaseSupplierListData(List<PurchaseSupplierData> items, long total, int page, int size) {}
