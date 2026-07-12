package com.ecommerce.billing;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Decides what a store's current plan is allowed to do. Paid features (bundles,
 * purchase orders) require the Professional tier; a store on Free (or an expired
 * paid plan) is treated as tier 0 and blocked. Enforced server-side so the rules
 * hold even if the UI gate is bypassed.
 */
@Service
public class EntitlementService {
    public static final String BUNDLES = "bundles";
    public static final String PURCHASE_ORDERS = "purchase_orders";

    private static final int PROFESSIONAL_TIER = 3;

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    public EntitlementService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    /** The store's effective plan tier (sortOrder). Free / expired / cancelled-past-due = 0. */
    public int currentTier(String storeId) {
        Subscription subscription = subscriptionRepository.findByStoreId(storeId).orElse(null);
        if (subscription == null || subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            return 0;
        }
        if (subscription.getExpiresAt() != null && subscription.getExpiresAt().isBefore(LocalDate.now())) {
            return 0;
        }
        return planRepository
                .findByCode(subscription.getPlanCode())
                .map(Plan::getSortOrder)
                .orElse(0);
    }

    public boolean isAllowed(String storeId, String feature) {
        return currentTier(storeId) >= minTier(feature);
    }

    /** Throw 403 if the store's plan doesn't cover the feature. */
    public void require(String storeId, String feature) {
        if (!isAllowed(storeId, feature)) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Your current plan doesn't include " + label(feature)
                            + ". Upgrade to the Professional plan to use it.");
        }
    }

    public EntitlementsData entitlements(String storeId) {
        int tier = currentTier(storeId);
        Subscription subscription = subscriptionRepository.findByStoreId(storeId).orElse(null);
        String planCode = subscription == null ? "free" : subscription.getPlanCode();
        String planName = planRepository.findByCode(planCode == null ? "free" : planCode)
                .map(Plan::getName)
                .orElse("Free");
        return new EntitlementsData(
                tier,
                planCode == null ? "free" : planCode,
                planName,
                tier >= minTier(BUNDLES),
                tier >= minTier(PURCHASE_ORDERS));
    }

    private static int minTier(String feature) {
        return switch (feature) {
            case BUNDLES, PURCHASE_ORDERS -> PROFESSIONAL_TIER;
            default -> 0;
        };
    }

    private static String label(String feature) {
        return switch (feature) {
            case BUNDLES -> "Bundles & multi-packs";
            case PURCHASE_ORDERS -> "Purchase orders";
            default -> "this feature";
        };
    }
}
