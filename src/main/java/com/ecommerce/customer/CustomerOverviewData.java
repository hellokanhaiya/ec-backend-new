package com.ecommerce.customer;

public record CustomerOverviewData(
        long totalCustomers,
        long active,
        long inactive,
        long blocked,
        long newThisMonth) {}
