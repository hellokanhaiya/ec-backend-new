package com.ecommerce.billing;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the three subscription tiers on startup. Idempotent: it upserts by code
 * so restarts refresh pricing/features without creating duplicates.
 */
@Component
public class PlanSeeder implements ApplicationRunner {
    private final PlanRepository planRepository;

    public PlanSeeder(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        upsert(
                "free",
                "Free",
                "Everything you need to start selling.",
                new BigDecimal("0"),
                new BigDecimal("0"),
                100,
                false,
                0,
                List.of(
                        "Up to 20 products",
                        "1 staff account",
                        "Online store & storefront",
                        "Community support"),
                List.of("Trials", "Hobby stores", "New sellers"));

        upsert(
                "basic",
                "Basic",
                "For new stores finding their feet.",
                new BigDecimal("499"),
                new BigDecimal("4990"),
                500,
                false,
                1,
                List.of(
                        "Up to 100 products",
                        "1 staff account",
                        "Online store & storefront",
                        "Basic sales analytics",
                        "Email support"),
                List.of("Home businesses", "Solo sellers", "Local retail"));

        upsert(
                "growth",
                "Growth",
                "For growing brands that need more firepower.",
                new BigDecimal("1499"),
                new BigDecimal("14990"),
                2500,
                true,
                2,
                List.of(
                        "Up to 2,000 products",
                        "5 staff accounts",
                        "Abandoned cart recovery",
                        "Discounts & marketing campaigns",
                        "Advanced analytics & reports",
                        "Priority chat support"),
                List.of("D2C brands", "Restaurants & cafés", "Boutiques"));

        upsert(
                "professional",
                "Professional",
                "For established businesses running at scale.",
                new BigDecimal("3999"),
                new BigDecimal("39990"),
                10000,
                false,
                3,
                List.of(
                        "Unlimited products",
                        "Unlimited staff accounts",
                        "Purchase orders & inventory",
                        "Bundles & multi-packs",
                        "API access & webhooks",
                        "Dedicated account manager"),
                List.of("Multi-store retailers", "Enterprises", "Wholesale & distribution"));
    }

    private void upsert(
            String code,
            String name,
            String tagline,
            BigDecimal monthly,
            BigDecimal yearly,
            int credits,
            boolean highlighted,
            int sortOrder,
            List<String> features,
            List<String> industries) {
        Plan plan = planRepository.findByCode(code).orElseGet(Plan::new);
        plan.setCode(code);
        plan.setName(name);
        plan.setTagline(tagline);
        plan.setMonthlyPrice(monthly);
        plan.setYearlyPrice(yearly);
        plan.setCurrencyCode("INR");
        plan.setCurrencySymbol("₹");
        plan.setCredits(credits);
        plan.setHighlighted(highlighted);
        plan.setSortOrder(sortOrder);
        plan.setActive(true);
        plan.getFeatures().clear();
        plan.getFeatures().addAll(features);
        plan.getIndustries().clear();
        plan.getIndustries().addAll(industries);
        planRepository.save(plan);
    }
}
