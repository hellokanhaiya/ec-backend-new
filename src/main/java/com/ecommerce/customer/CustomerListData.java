package com.ecommerce.customer;

import java.util.List;

public record CustomerListData(List<CustomerSummaryData> items, long total, int page, int size) {}
