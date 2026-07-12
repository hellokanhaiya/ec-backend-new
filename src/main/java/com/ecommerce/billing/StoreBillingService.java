package com.ecommerce.billing;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StoreBillingService {
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingTransactionRepository transactionRepository;
    private final RazorpayClient razorpayClient;
    private final BigDecimal taxPercent;

    public StoreBillingService(
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            BillingTransactionRepository transactionRepository,
            RazorpayClient razorpayClient,
            @Value("${billing.tax-percent:18}") BigDecimal taxPercent) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.transactionRepository = transactionRepository;
        this.razorpayClient = razorpayClient;
        this.taxPercent = taxPercent == null ? new BigDecimal("18") : taxPercent;
    }

    public List<PlanData> getPlans() {
        return planRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(this::toPlanData)
                .toList();
    }

    public BillingOverviewData getOverview(String storeId) {
        return new BillingOverviewData(
                getSubscription(storeId),
                getPlans(),
                transactionRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                        .map(this::toTransactionData)
                        .toList());
    }

    public SubscriptionData getSubscription(String storeId) {
        Subscription subscription = subscriptionRepository.findByStoreId(storeId).orElse(null);
        if (subscription == null) {
            // Every store is on the Free plan by default — no DB row needed until
            // they upgrade to a paid plan.
            return freeSubscription();
        }
        // Lazily flip an active-but-past-due subscription to expired so the UI and
        // the stored state agree without a scheduled job.
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE
                && subscription.getExpiresAt() != null
                && subscription.getExpiresAt().isBefore(LocalDate.now())) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);
        }
        return toSubscriptionData(subscription);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    // The default plan for a store that has never paid: active, no expiry.
    private SubscriptionData freeSubscription() {
        Plan free = planRepository.findByCode("free").orElse(null);
        int credits = free == null ? 0 : free.getCredits();
        String name = free == null ? "Free" : free.getName();
        return new SubscriptionData("active", "free", name, null, null, null, null, false, credits, false, null);
    }

    public CheckoutData createCheckout(String storeId, CheckoutRequest request) {
        if (request == null || request.planCode() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "planCode is required");
        }
        Plan plan = planRepository
                .findByCode(request.planCode().trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Plan not found"));
        BillingCycle cycle = BillingCycle.from(request.cycle());
        BigDecimal base = money(cycle == BillingCycle.YEARLY ? plan.getYearlyPrice() : plan.getMonthlyPrice());
        if (base.signum() <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "The Free plan does not require payment");
        }
        // GST/tax added on top of the plan price. The total is the authoritative
        // charge — the Razorpay order and the frontend modal both use it.
        BigDecimal taxAmount = money(base.multiply(taxPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        BigDecimal total = money(base.add(taxAmount));
        long amountInPaise = total.movePointRight(2).longValueExact();

        String receipt = "sub_" + storeId + "_" + Instant.now().toEpochMilli();
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("storeId", storeId);
        notes.put("planCode", plan.getCode());
        notes.put("cycle", cycle.apiValue());
        String orderId = razorpayClient.createOrder(amountInPaise, plan.getCurrencyCode(), receipt, notes);

        BillingTransaction transaction = new BillingTransaction();
        transaction.setStoreId(storeId);
        transaction.setPlanCode(plan.getCode());
        transaction.setBillingCycle(cycle);
        transaction.setAmount(total);
        transaction.setCurrencyCode(plan.getCurrencyCode());
        transaction.setStatus(TransactionStatus.CREATED);
        transaction.setRazorpayOrderId(orderId);
        transactionRepository.save(transaction);

        return new CheckoutData(
                orderId,
                razorpayClient.keyId(),
                amountInPaise,
                plan.getCurrencyCode(),
                plan.getCode(),
                plan.getName(),
                cycle.apiValue(),
                base,
                taxPercent,
                taxAmount,
                total,
                razorpayClient.isTestMode());
    }

    public SubscriptionData verifyPayment(String storeId, VerifyRequest request) {
        if (request == null || request.razorpayOrderId() == null || request.razorpayPaymentId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing payment details");
        }
        BillingTransaction transaction = transactionRepository
                .findByStoreIdAndRazorpayOrderId(storeId, request.razorpayOrderId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Unknown order for this store"));

        boolean valid = razorpayClient.verifySignature(
                request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature());
        if (!valid) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            throw new ResponseStatusException(BAD_REQUEST, "Payment signature verification failed");
        }

        transaction.setStatus(TransactionStatus.PAID);
        transaction.setRazorpayPaymentId(request.razorpayPaymentId());
        transaction.setPaidAt(Instant.now());
        transactionRepository.save(transaction);

        activateOrRenew(storeId, transaction);
        return getSubscription(storeId);
    }

    private void activateOrRenew(String storeId, BillingTransaction transaction) {
        Plan plan = planRepository
                .findByCode(transaction.getPlanCode())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Plan not found"));
        Subscription subscription = subscriptionRepository.findByStoreId(storeId).orElseGet(() -> {
            Subscription created = new Subscription();
            created.setStoreId(storeId);
            return created;
        });

        LocalDate today = LocalDate.now();
        // Renewing the same plan while still active stacks on the remaining time;
        // otherwise the term starts today.
        boolean stacking = subscription.getStatus() == SubscriptionStatus.ACTIVE
                && subscription.getExpiresAt() != null
                && !subscription.getExpiresAt().isBefore(today)
                && plan.getCode().equals(subscription.getPlanCode());
        LocalDate anchor = stacking ? subscription.getExpiresAt() : today;

        subscription.setPlanCode(plan.getCode());
        subscription.setBillingCycle(transaction.getBillingCycle());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(stacking && subscription.getStartedAt() != null ? subscription.getStartedAt() : today);
        subscription.setExpiresAt(transaction.getBillingCycle().advance(anchor));
        subscription.setCreditsRemaining(subscription.getCreditsRemaining() + plan.getCredits());
        subscription.setLastRazorpayOrderId(transaction.getRazorpayOrderId());
        subscription.setLastRazorpayPaymentId(transaction.getRazorpayPaymentId());
        subscription.setAutoRenew(true);
        subscriptionRepository.save(subscription);
    }

    public SubscriptionData cancel(String storeId) {
        Subscription subscription = subscriptionRepository
                .findByStoreId(storeId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No active subscription"));
        // Cancel = stop auto-renew; the plan stays usable until it expires.
        subscription.setAutoRenew(false);
        if (subscription.getExpiresAt() != null && subscription.getExpiresAt().isBefore(LocalDate.now())) {
            subscription.setStatus(SubscriptionStatus.CANCELLED);
        }
        subscriptionRepository.save(subscription);
        return getSubscription(storeId);
    }

    // ----- mappers ----------------------------------------------------------

    private PlanData toPlanData(Plan plan) {
        return new PlanData(
                plan.getCode(),
                plan.getName(),
                plan.getTagline(),
                plan.getMonthlyPrice(),
                plan.getYearlyPrice(),
                plan.getCurrencyCode(),
                plan.getCurrencySymbol(),
                plan.getCredits(),
                plan.isHighlighted(),
                plan.getSortOrder(),
                List.copyOf(plan.getFeatures()),
                List.copyOf(plan.getIndustries()));
    }

    private SubscriptionData toSubscriptionData(Subscription subscription) {
        LocalDate today = LocalDate.now();
        boolean expired = subscription.getStatus() == SubscriptionStatus.EXPIRED
                || (subscription.getExpiresAt() != null && subscription.getExpiresAt().isBefore(today));
        Integer daysRemaining = subscription.getExpiresAt() == null
                ? null
                : (int) ChronoUnit.DAYS.between(today, subscription.getExpiresAt());
        String planName = subscription.getPlanCode() == null
                ? null
                : planRepository.findByCode(subscription.getPlanCode()).map(Plan::getName).orElse(null);
        return new SubscriptionData(
                subscription.getStatus().apiValue(),
                subscription.getPlanCode(),
                planName,
                subscription.getBillingCycle() == null ? null : subscription.getBillingCycle().apiValue(),
                subscription.getStartedAt(),
                subscription.getExpiresAt(),
                daysRemaining,
                expired,
                subscription.getCreditsRemaining(),
                subscription.isAutoRenew(),
                subscription.getLastRazorpayPaymentId());
    }

    private BillingTransactionData toTransactionData(BillingTransaction transaction) {
        return new BillingTransactionData(
                transaction.getPlanCode(),
                transaction.getBillingCycle().apiValue(),
                transaction.getAmount(),
                transaction.getCurrencyCode(),
                transaction.getStatus().apiValue(),
                transaction.getRazorpayOrderId(),
                transaction.getRazorpayPaymentId(),
                transaction.getCreatedAt(),
                transaction.getPaidAt());
    }
}
