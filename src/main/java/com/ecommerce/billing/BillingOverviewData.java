package com.ecommerce.billing;

import java.util.List;

/** Everything the Billing & Plans screen needs in one call. */
public record BillingOverviewData(
        SubscriptionData subscription,
        List<PlanData> plans,
        List<BillingTransactionData> transactions) {}
