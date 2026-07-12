package com.ecommerce.vendor;

public record VendorOverviewData(
        long totalVendors,
        long approvedVendors,
        long pendingVendors,
        long suspendedVendors) {}
