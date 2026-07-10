package com.ecommerce.promotion;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.ecommerce.order.StoreOrderLineItem;
import com.ecommerce.order.StoreOrderRepository;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StorePromotionService {
    private final StorePromotionRepository promotionRepository;
    private final StoreOrderRepository orderRepository;
    private final ProductRepository productRepository;

    public StorePromotionService(
            StorePromotionRepository promotionRepository,
            StoreOrderRepository orderRepository,
            ProductRepository productRepository) {
        this.promotionRepository = promotionRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    public PromotionListData list(String storeId, String search, String type, String status, int page, int size) {
        List<PromotionData> items = promotionRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .filter(promotion -> matchesSearch(promotion, search))
                .filter(promotion -> matchesType(promotion, type))
                .filter(promotion -> matchesStatus(promotion, status))
                .map(this::toData)
                .toList();
        return paginate(items, page, size);
    }

    public PromotionData get(String storeId, String publicPromotionId) {
        return toData(require(storeId, publicPromotionId));
    }

    public PromotionData create(String storeId, String ownerPublicUserId, PromotionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        if (request.code() != null && !request.code().isBlank()) {
            String normalizedCode = normalizeCode(request.code());
            if (promotionRepository.findByStoreIdAndCodeIgnoreCase(storeId, normalizedCode).isPresent()) {
                throw new ResponseStatusException(BAD_REQUEST, "A promotion with this code already exists");
            }
        }
        StorePromotion promotion = new StorePromotion();
        promotion.setStoreId(storeId);
        promotion.setOwnerPublicUserId(ownerPublicUserId);
        applyRequest(promotion, request);
        return toData(promotionRepository.save(promotion));
    }

    public PromotionData update(String storeId, String publicPromotionId, PromotionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        StorePromotion promotion = require(storeId, publicPromotionId);
        if (request.code() != null && !request.code().isBlank()) {
            String normalizedCode = normalizeCode(request.code());
            Optional<StorePromotion> existing = promotionRepository.findByStoreIdAndCodeIgnoreCase(storeId, normalizedCode);
            if (existing.isPresent() && !existing.get().getPublicPromotionId().equals(publicPromotionId)) {
                throw new ResponseStatusException(BAD_REQUEST, "A promotion with this code already exists");
            }
        }
        long usageCount = promotion.getUsageCount();
        applyRequest(promotion, request);
        promotion.setUsageCount(usageCount);
        return toData(promotionRepository.save(promotion));
    }

    public void delete(String storeId, String publicPromotionId) {
        promotionRepository.delete(require(storeId, publicPromotionId));
    }

    public PromotionData activate(String storeId, String publicPromotionId) {
        StorePromotion promotion = require(storeId, publicPromotionId);
        promotion.setStatus(PromotionStatus.ACTIVE);
        return toData(promotionRepository.save(promotion));
    }

    public PromotionData deactivate(String storeId, String publicPromotionId) {
        StorePromotion promotion = require(storeId, publicPromotionId);
        promotion.setStatus(PromotionStatus.PAUSED);
        return toData(promotionRepository.save(promotion));
    }

    public PromotionApplyData resolve(String storeId, PromotionApplyRequest request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            return notFound("Promotion code is required", request);
        }
        Optional<StorePromotion> promotion = promotionRepository.findByStoreIdAndCodeIgnoreCase(
                storeId, normalizeCode(request.code()));
        if (promotion.isEmpty()) {
            return notFound("Promotion not found", request);
        }
        return evaluate(storeId, promotion.get(), request.customerPublicId(), request.shippingCharge(), request.items());
    }

    public Optional<PromotionApplyData> resolveForOrder(
            String storeId,
            String code,
            String customerPublicId,
            BigDecimal shippingCharge,
            List<StoreOrderLineItem> lineItems) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        Optional<StorePromotion> promotion = promotionRepository.findByStoreIdAndCodeIgnoreCase(storeId, normalizeCode(code));
        if (promotion.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(evaluate(storeId, promotion.get(), customerPublicId, shippingCharge, toItemRequests(lineItems)));
    }

    public boolean hasPromotionCode(String storeId, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return promotionRepository.findByStoreIdAndCodeIgnoreCase(storeId, normalizeCode(code)).isPresent();
    }

    public void markRedeemed(String storeId, String publicPromotionId) {
        if (publicPromotionId == null || publicPromotionId.isBlank()) {
            return;
        }
        StorePromotion promotion = require(storeId, publicPromotionId);
        promotion.setUsageCount(promotion.getUsageCount() + 1);
        promotionRepository.save(promotion);
    }

    private PromotionApplyData evaluate(
            String storeId,
            StorePromotion promotion,
            String customerPublicId,
            BigDecimal shippingCharge,
            List<PromotionItemRequest> items) {
        List<PromotionItemRequest> safeItems = items == null ? List.of() : items.stream()
                .filter(item -> item != null && item.productPublicId() != null && !item.productPublicId().isBlank())
                .map(item -> new PromotionItemRequest(
                        item.productPublicId(),
                        item.quantity() == null ? 0 : Math.max(item.quantity(), 0),
                        money(item.unitPrice())))
                .filter(item -> item.quantity() > 0)
                .toList();
        BigDecimal subtotal = safeItems.stream()
                .map(item -> money(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String runtimeStatus = runtimeStatus(promotion);
        if (!"active".equals(runtimeStatus)) {
            return applyResult(
                    promotion,
                    false,
                    "Promotion is " + runtimeStatus,
                    subtotal,
                    shippingCharge,
                    BigDecimal.ZERO,
                    false,
                    List.of());
        }

        if (!customerEligible(promotion, customerPublicId)) {
            return applyResult(
                    promotion,
                    false,
                    "Customer is not eligible for this promotion",
                    subtotal,
                    shippingCharge,
                    BigDecimal.ZERO,
                    false,
                    List.of());
        }

        if (promotion.getUsageLimitMode() != null) {
            if ("total".equalsIgnoreCase(promotion.getUsageLimitMode())) {
                Integer limit = promotion.getUsageLimitTotal();
                if (limit != null && limit > 0 && promotion.getUsageCount() >= limit) {
                    return applyResult(
                            promotion,
                            false,
                            "Promotion usage limit has been reached",
                            subtotal,
                            shippingCharge,
                            BigDecimal.ZERO,
                            false,
                            List.of());
                }
            }
            if ("per_customer".equalsIgnoreCase(promotion.getUsageLimitMode())) {
                Integer limit = promotion.getUsageLimitPerCustomer();
                if (limit != null && limit > 0 && customerPublicId != null && !customerPublicId.isBlank()) {
                    long customerUsage = orderRepository.countByStoreIdAndCustomerPublicIdAndPromotionPublicId(
                            storeId, customerPublicId, promotion.getPublicPromotionId());
                    if (customerUsage >= limit) {
                        return applyResult(
                                promotion,
                                false,
                                "Customer usage limit has been reached",
                                subtotal,
                                shippingCharge,
                                BigDecimal.ZERO,
                                false,
                                List.of());
                    }
                }
            }
        }

        Map<String, Product> productsById = productRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .collect(Collectors.toMap(Product::getPublicProductId, product -> product, (left, right) -> left));

        List<PromotionLineMatch> buyMatches = matchesForTarget(promotion.getType() == PromotionType.AMOUNT_OFF_PRODUCTS
                        ? promotion.getPrimaryTargetMode()
                        : promotion.getBuyTargetMode(),
                promotion.getType() == PromotionType.AMOUNT_OFF_PRODUCTS
                        ? parseList(promotion.getPrimaryTargetIds())
                        : parseList(promotion.getBuyTargetIds()),
                safeItems,
                productsById);
        List<PromotionLineMatch> rewardMatches = promotion.getType() == PromotionType.BUY_X_GET_Y
                ? matchesForTarget(promotion.getGetTargetMode(), parseList(promotion.getGetTargetIds()), safeItems, productsById)
                : List.of();

        if (promotion.getType() == PromotionType.AMOUNT_OFF_PRODUCTS && buyMatches.isEmpty()) {
            return applyResult(
                    promotion,
                    false,
                    "No qualifying products were found in the cart",
                    subtotal,
                    shippingCharge,
                    BigDecimal.ZERO,
                    false,
                    List.of());
        }
        if (promotion.getType() == PromotionType.BUY_X_GET_Y) {
            int buyQuantity = promotion.getBuyQuantity() == null ? 0 : promotion.getBuyQuantity();
            int getQuantity = promotion.getGetQuantity() == null ? 0 : promotion.getGetQuantity();
            if (buyQuantity <= 0 || getQuantity <= 0) {
                return applyResult(
                        promotion,
                        false,
                        "Buy and get quantities must be greater than zero",
                        subtotal,
                        shippingCharge,
                        BigDecimal.ZERO,
                        false,
                        List.of());
            }
            int qualifyingUnits = buyMatches.stream().mapToInt(PromotionLineMatch::quantity).sum();
            if (qualifyingUnits < buyQuantity) {
                return applyResult(
                        promotion,
                        false,
                        "Cart does not meet the buy quantity requirement",
                        subtotal,
                        shippingCharge,
                        BigDecimal.ZERO,
                        false,
                        List.of());
            }
            int bundles = qualifyingUnits / buyQuantity;
            int rewardUnitsNeeded = bundles * getQuantity;
            if (rewardMatches.isEmpty()) {
                return applyResult(
                        promotion,
                        false,
                        "No reward products were found in the cart",
                        subtotal,
                        shippingCharge,
                        BigDecimal.ZERO,
                        false,
                        List.of());
            }

            List<PromotionAppliedItemData> appliedItems = new ArrayList<>();
            int remainingRewardUnits = rewardUnitsNeeded;
            BigDecimal discountAmount = BigDecimal.ZERO;
            List<PromotionLineMatch> orderedRewardMatches = rewardMatches.stream()
                    .sorted(Comparator.comparing(PromotionLineMatch::unitPrice))
                    .toList();
            for (PromotionLineMatch match : orderedRewardMatches) {
                if (remainingRewardUnits <= 0) {
                    break;
                }
                int appliedUnits = Math.min(match.quantity(), remainingRewardUnits);
                if (appliedUnits <= 0) {
                    continue;
                }
                BigDecimal perUnitDiscount = switch (promotion.getRewardType()) {
                    case PERCENTAGE -> match.unitPrice()
                            .multiply(promotion.getRewardValue().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
                    case FIXED -> promotion.getRewardValue().min(match.unitPrice());
                    default -> match.unitPrice();
                };
                perUnitDiscount = perUnitDiscount.max(BigDecimal.ZERO);
                BigDecimal lineDiscount = money(perUnitDiscount.multiply(BigDecimal.valueOf(appliedUnits)));
                discountAmount = discountAmount.add(lineDiscount);
                appliedItems.add(new PromotionAppliedItemData(
                        match.productPublicId(),
                        match.name(),
                        appliedUnits,
                        match.unitPrice(),
                        lineDiscount));
                remainingRewardUnits -= appliedUnits;
            }

            if (discountAmount.signum() == 0) {
                return applyResult(
                        promotion,
                        false,
                        "No reward items were available to discount",
                        subtotal,
                        shippingCharge,
                        BigDecimal.ZERO,
                        false,
                        List.of());
            }

            BigDecimal cappedDiscount = discountAmount.min(subtotal);
            boolean freeShipping = promotion.getType() == PromotionType.FREE_SHIPPING;
            BigDecimal resolvedShipping = shippingCharge == null ? BigDecimal.ZERO : money(shippingCharge);
            BigDecimal shippingSavings = freeShipping ? resolvedShipping : BigDecimal.ZERO;
            BigDecimal finalShippingCharge = freeShipping ? BigDecimal.ZERO : resolvedShipping;
            BigDecimal finalTotal = subtotal.subtract(cappedDiscount).add(finalShippingCharge);
            return new PromotionApplyData(
                    true,
                    true,
                    null,
                    promotion.getPublicPromotionId(),
                    promotion.getCode(),
                    promotion.getName(),
                    safeApiValue(promotion.getType()),
                    safeApiValue(promotion.getStatus()),
                    money(subtotal),
                    money(cappedDiscount),
                    freeShipping,
                    money(shippingSavings),
                    money(finalShippingCharge),
                    money(finalTotal),
                    appliedItems,
                    promotionSummary(promotion));
        }

        BigDecimal qualifyingSubtotal = switch (promotion.getType()) {
            case AMOUNT_OFF_PRODUCTS -> matchSubtotal(buyMatches);
            case AMOUNT_OFF_ORDER, FREE_SHIPPING -> subtotal;
            default -> subtotal;
        };
        if (qualifyingSubtotal.signum() == 0 && promotion.getType() != PromotionType.FREE_SHIPPING) {
            return applyResult(
                    promotion,
                    false,
                    "Promotion does not match the current cart",
                    subtotal,
                    shippingCharge,
                    BigDecimal.ZERO,
                    false,
                    List.of());
        }

        if (promotion.getMinimumRequirement() != null && !promotion.getMinimumRequirement().equalsIgnoreCase("none")) {
            if ("amount".equalsIgnoreCase(promotion.getMinimumRequirement())) {
                BigDecimal minimum = promotion.getMinimumAmount() == null ? BigDecimal.ZERO : promotion.getMinimumAmount();
                if (subtotal.compareTo(minimum) < 0) {
                    return applyResult(
                            promotion,
                            false,
                            "Cart does not meet the minimum spend",
                            subtotal,
                            shippingCharge,
                            BigDecimal.ZERO,
                            false,
                            List.of());
                }
            }
            if ("quantity".equalsIgnoreCase(promotion.getMinimumRequirement())) {
                int quantity = safeItems.stream().mapToInt(PromotionItemRequest::quantity).sum();
                int minimumQuantity = promotion.getMinimumQuantity() == null ? 0 : promotion.getMinimumQuantity();
                if (quantity < minimumQuantity) {
                    return applyResult(
                            promotion,
                            false,
                            "Cart does not meet the minimum quantity",
                            subtotal,
                            shippingCharge,
                            BigDecimal.ZERO,
                            false,
                            List.of());
                }
            }
        }

        BigDecimal discountAmount = BigDecimal.ZERO;
        List<PromotionAppliedItemData> appliedItems = new ArrayList<>();
        if (promotion.getType() == PromotionType.AMOUNT_OFF_PRODUCTS) {
            discountAmount = discountedAmountForTarget(promotion.getValueType(), promotion.getValue(), buyMatches);
            appliedItems.addAll(targetItemData(buyMatches, discountAmount));
        } else if (promotion.getType() == PromotionType.AMOUNT_OFF_ORDER) {
            discountAmount = discountFromValueType(promotion.getValueType(), promotion.getValue(), subtotal);
            appliedItems.add(new PromotionAppliedItemData(null, promotion.getName(), 1, subtotal, discountAmount));
        } else if (promotion.getType() == PromotionType.FREE_SHIPPING) {
            discountAmount = BigDecimal.ZERO;
        }
        discountAmount = discountAmount.min(subtotal);

        boolean freeShipping = promotion.getType() == PromotionType.FREE_SHIPPING;
        BigDecimal resolvedShipping = shippingCharge == null ? BigDecimal.ZERO : money(shippingCharge);
        BigDecimal shippingSavings = freeShipping ? resolvedShipping : BigDecimal.ZERO;
        BigDecimal finalShippingCharge = freeShipping ? BigDecimal.ZERO : resolvedShipping;
        BigDecimal finalTotal = subtotal.subtract(discountAmount).add(finalShippingCharge);
        return new PromotionApplyData(
                true,
                true,
                null,
                promotion.getPublicPromotionId(),
                promotion.getCode(),
                promotion.getName(),
                safeApiValue(promotion.getType()),
                safeApiValue(promotion.getStatus()),
                money(subtotal),
                money(discountAmount),
                freeShipping,
                money(shippingSavings),
                money(finalShippingCharge),
                money(finalTotal),
                appliedItems,
                promotionSummary(promotion));
    }

    private PromotionApplyData applyResult(
            StorePromotion promotion,
            boolean qualifies,
            String reason,
            BigDecimal subtotal,
            BigDecimal shippingCharge,
            BigDecimal discountAmount,
            boolean freeShipping,
            List<PromotionAppliedItemData> appliedItems) {
        BigDecimal resolvedShipping = shippingCharge == null ? BigDecimal.ZERO : money(shippingCharge);
        BigDecimal shippingSavings = freeShipping ? resolvedShipping : BigDecimal.ZERO;
        BigDecimal finalShippingCharge = freeShipping ? BigDecimal.ZERO : resolvedShipping;
        BigDecimal finalTotal = subtotal.subtract(discountAmount).add(finalShippingCharge);
        return new PromotionApplyData(
                true,
                qualifies,
                reason,
                promotion.getPublicPromotionId(),
                promotion.getCode(),
                promotion.getName(),
                safeApiValue(promotion.getType()),
                safeApiValue(promotion.getStatus()),
                money(subtotal),
                money(discountAmount),
                freeShipping,
                money(shippingSavings),
                money(finalShippingCharge),
                money(finalTotal),
                appliedItems,
                promotionSummary(promotion));
    }

    private PromotionApplyData notFound(String reason, PromotionApplyRequest request) {
        BigDecimal subtotal = request == null || request.items() == null
                ? BigDecimal.ZERO
                : request.items().stream()
                        .filter(item -> item != null && item.quantity() != null && item.unitPrice() != null)
                        .map(item -> money(item.unitPrice().multiply(BigDecimal.valueOf(Math.max(item.quantity(), 0)))))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shippingCharge = request == null || request.shippingCharge() == null
                ? BigDecimal.ZERO
                : money(request.shippingCharge());
        return new PromotionApplyData(
                false,
                false,
                reason,
                null,
                normalizeCode(request == null ? null : request.code()),
                null,
                null,
                null,
                money(subtotal),
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                shippingCharge,
                money(subtotal.add(shippingCharge)),
                List.of(),
                null);
    }

    private void applyRequest(StorePromotion promotion, PromotionRequest request) {
        promotion.setName(normalize(request.name()));
        if (promotion.getName() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Promotion name is required");
        }
        promotion.setCode(normalizeCode(request.code()));
        promotion.setMethod(PromotionMethod.from(request.method()));
        promotion.setType(PromotionType.from(request.type()));
        promotion.setValueType(PromotionValueType.from(request.valueType()));
        promotion.setValue(money(request.value() == null ? BigDecimal.ZERO : request.value()));
        promotion.setCustomerEligibility(firstNonBlank(request.customerEligibility(), "all"));
        promotion.setCustomerSegment(normalize(request.customerSegment()));
        promotion.setCustomerIds(join(request.customerIds()));
        promotion.setMinimumRequirement(firstNonBlank(request.minimumRequirement(), "none"));
        promotion.setMinimumAmount(money(request.minimumAmount() == null ? BigDecimal.ZERO : request.minimumAmount()));
        promotion.setMinimumQuantity(request.minimumQuantity());
        promotion.setUsageLimitMode(firstNonBlank(request.usageLimitMode(), "unlimited"));
        promotion.setUsageLimitTotal(request.usageLimitTotal());
        promotion.setUsageLimitPerCustomer(request.usageLimitPerCustomer());
        promotion.setAllowOrders(request.allowOrders() == null || request.allowOrders());
        promotion.setAllowProducts(request.allowProducts() == null || request.allowProducts());
        promotion.setAllowShipping(request.allowShipping() == null || request.allowShipping());
        promotion.setTags(join(request.tags()));
        promotion.setStartsAt(request.startsAt());
        promotion.setEndsAt(request.endsAt());
        promotion.setPrimaryTargetMode(firstNonBlank(request.primaryTargetMode(), "products"));
        promotion.setPrimaryTargetIds(join(request.primaryTargetIds()));
        promotion.setBuyTargetMode(firstNonBlank(request.buyTargetMode(), "products"));
        promotion.setBuyTargetIds(join(request.buyTargetIds()));
        promotion.setGetTargetMode(firstNonBlank(request.getTargetMode(), "products"));
        promotion.setGetTargetIds(join(request.getTargetIds()));
        promotion.setBuyQuantity(request.buyQuantity());
        promotion.setGetQuantity(request.getQuantity());
        promotion.setRewardType(PromotionRewardType.from(request.rewardType()));
        promotion.setRewardValue(money(request.rewardValue() == null ? BigDecimal.ZERO : request.rewardValue()));
        promotion.setStatus(PromotionStatus.from(request.status()));

        if (promotion.getType() == PromotionType.FREE_SHIPPING) {
            promotion.setValueType(PromotionValueType.FIXED);
            promotion.setValue(BigDecimal.ZERO);
            promotion.setRewardType(PromotionRewardType.FREE);
            promotion.setRewardValue(BigDecimal.ZERO);
        }
    }

    private PromotionData toData(StorePromotion promotion) {
        return new PromotionData(
                promotion.getId(),
                promotion.getPublicPromotionId(),
                promotion.getName(),
                promotion.getCode(),
                promotion.getMethod() == null ? "code" : promotion.getMethod().apiValue(),
                promotion.getType() == null ? "amount-off-products" : promotion.getType().apiValue(),
                promotion.getStatus() == null ? "draft" : promotion.getStatus().apiValue(),
                runtimeStatus(promotion),
                promotion.getValueType() == null ? null : promotion.getValueType().apiValue(),
                promotion.getValue(),
                promotion.getCustomerEligibility(),
                promotion.getCustomerSegment(),
                parseList(promotion.getCustomerIds()),
                promotion.getMinimumRequirement(),
                promotion.getMinimumAmount(),
                promotion.getMinimumQuantity(),
                promotion.getUsageLimitMode(),
                promotion.getUsageLimitTotal(),
                promotion.getUsageLimitPerCustomer(),
                promotion.getUsageCount(),
                promotion.isAllowOrders(),
                promotion.isAllowProducts(),
                promotion.isAllowShipping(),
                parseList(promotion.getTags()),
                promotion.getStartsAt(),
                promotion.getEndsAt(),
                promotion.getPrimaryTargetMode(),
                parseList(promotion.getPrimaryTargetIds()),
                promotion.getBuyTargetMode(),
                parseList(promotion.getBuyTargetIds()),
                promotion.getGetTargetMode(),
                parseList(promotion.getGetTargetIds()),
                promotion.getBuyQuantity(),
                promotion.getGetQuantity(),
                promotion.getRewardType() == null ? null : promotion.getRewardType().apiValue(),
                promotion.getRewardValue(),
                promotionSummary(promotion),
                promotion.getCreatedAt(),
                promotion.getUpdatedAt());
    }

    private PromotionListData paginate(List<PromotionData> items, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 0);
        if (safeSize == 0) {
            return new PromotionListData(items, items.size(), safePage, safeSize);
        }
        int from = Math.min((safePage - 1) * safeSize, items.size());
        int to = Math.min(from + safeSize, items.size());
        return new PromotionListData(items.subList(from, to), items.size(), safePage, safeSize);
    }

    private StorePromotion require(String storeId, String publicPromotionId) {
        return promotionRepository.findByStoreIdAndPublicPromotionId(storeId, publicPromotionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Promotion not found"));
    }

    private boolean matchesSearch(StorePromotion promotion, String search) {
        String query = normalize(search);
        if (query == null) {
            return true;
        }
        return contains(promotion.getName(), query)
                || contains(promotion.getCode(), query)
                || contains(promotion.getType() == null ? null : promotion.getType().apiValue(), query)
                || contains(promotionSummary(promotion), query);
    }

    private boolean matchesType(StorePromotion promotion, String type) {
        String query = normalize(type);
        return query == null || (promotion.getType() != null && promotion.getType().apiValue().equalsIgnoreCase(query));
    }

    private boolean matchesStatus(StorePromotion promotion, String status) {
        String query = normalize(status);
        return query == null || runtimeStatus(promotion).equalsIgnoreCase(query)
                || (promotion.getStatus() != null && promotion.getStatus().apiValue().equalsIgnoreCase(query));
    }

    private String runtimeStatus(StorePromotion promotion) {
        Instant now = Instant.now();
        if (promotion.getStatus() == PromotionStatus.ARCHIVED) {
            return "archived";
        }
        if (promotion.getStatus() == PromotionStatus.PAUSED) {
            return "paused";
        }
        if (promotion.getStartsAt() != null && now.isBefore(promotion.getStartsAt())) {
            return "scheduled";
        }
        if (promotion.getEndsAt() != null && now.isAfter(promotion.getEndsAt())) {
            return "expired";
        }
        if (promotion.getStatus() == PromotionStatus.DRAFT) {
            return "draft";
        }
        return "active";
    }

    private boolean customerEligible(StorePromotion promotion, String customerPublicId) {
        if (promotion.getCustomerEligibility() == null || "all".equalsIgnoreCase(promotion.getCustomerEligibility())) {
            return true;
        }
        if ("segments".equalsIgnoreCase(promotion.getCustomerEligibility())) {
            return promotion.getCustomerSegment() != null && !promotion.getCustomerSegment().isBlank();
        }
        if ("specific".equalsIgnoreCase(promotion.getCustomerEligibility())) {
            return customerPublicId != null && parseList(promotion.getCustomerIds()).contains(customerPublicId);
        }
        return true;
    }

    private List<PromotionLineMatch> matchesForTarget(
            String targetMode,
            List<String> targetIds,
            List<PromotionItemRequest> items,
            Map<String, Product> productsById) {
        PromotionTargetMode mode = PromotionTargetMode.from(targetMode);
        Set<String> ids = targetIds == null ? Set.of() : new LinkedHashSet<>(targetIds);
        return items.stream()
                .flatMap(item -> {
                    Product product = productsById.get(item.productPublicId());
                    if (product == null) {
                        return java.util.stream.Stream.empty();
                    }
                    boolean matches = switch (mode) {
                        case ALL -> true;
                        case PRODUCTS -> ids.isEmpty() || ids.contains(product.getPublicProductId());
                        case CATEGORIES -> product.getCategoryPublicId() != null && ids.contains(product.getCategoryPublicId());
                        case COLLECTIONS -> false;
                    };
                    if (!matches) {
                        return java.util.stream.Stream.empty();
                    }
                    return java.util.stream.Stream.of(new PromotionLineMatch(
                            item.productPublicId(),
                            defaultString(product.getTitle(), item.productPublicId()),
                            Math.max(item.quantity(), 0),
                            money(item.unitPrice())));
                })
                .collect(Collectors.toList());
    }

    private BigDecimal discountedAmountForTarget(
            PromotionValueType valueType, BigDecimal value, List<PromotionLineMatch> matches) {
        BigDecimal subtotal = matchSubtotal(matches);
        return discountFromValueType(valueType, value, subtotal);
    }

    private BigDecimal discountFromValueType(PromotionValueType valueType, BigDecimal value, BigDecimal subtotal) {
        BigDecimal resolvedValue = value == null ? BigDecimal.ZERO : value;
        if (valueType == PromotionValueType.PERCENTAGE) {
            BigDecimal discount = subtotal.multiply(resolvedValue).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            return money(discount.min(subtotal));
        }
        return money(resolvedValue.min(subtotal));
    }

    private List<PromotionAppliedItemData> targetItemData(List<PromotionLineMatch> matches, BigDecimal totalDiscount) {
        if (matches.isEmpty() || totalDiscount.signum() == 0) {
            return List.of();
        }
        BigDecimal subtotal = matchSubtotal(matches);
        if (subtotal.signum() == 0) {
            return List.of();
        }
        return matches.stream()
                .map(match -> {
                    BigDecimal lineSubtotal = match.unitPrice().multiply(BigDecimal.valueOf(match.quantity()));
                    BigDecimal share = lineSubtotal.divide(subtotal, 8, RoundingMode.HALF_UP)
                            .multiply(totalDiscount);
                    return new PromotionAppliedItemData(
                            match.productPublicId(),
                            match.name(),
                            match.quantity(),
                            match.unitPrice(),
                            money(share));
                })
                .toList();
    }

    private BigDecimal matchSubtotal(List<PromotionLineMatch> matches) {
        return matches.stream()
                .map(match -> match.unitPrice().multiply(BigDecimal.valueOf(match.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<PromotionItemRequest> toItemRequests(List<StoreOrderLineItem> lineItems) {
        if (lineItems == null) {
            return List.of();
        }
        return lineItems.stream()
                .map(item -> new PromotionItemRequest(
                        item.getProductPublicId(),
                        item.getQuantity() == null ? 0 : item.getQuantity(),
                        item.getUnitPrice()))
                .toList();
    }

    private boolean contains(String value, String query) {
        return normalize(value) != null && normalize(value).contains(query.toLowerCase(Locale.ROOT));
    }

    private String promotionSummary(StorePromotion promotion) {
        StringBuilder builder = new StringBuilder();
        String typeName = promotion.getType() == null ? "amount-off-products" : promotion.getType().apiValue();
        builder.append(typeName.replace('-', ' '));
        if (promotion.getType() == PromotionType.BUY_X_GET_Y && promotion.getBuyQuantity() != null && promotion.getGetQuantity() != null) {
            builder.append(" · buy ").append(promotion.getBuyQuantity()).append(" get ").append(promotion.getGetQuantity());
        } else if (promotion.getValue() != null && promotion.getValue().signum() > 0) {
            String valueTypeName = promotion.getValueType() == null ? "fixed" : promotion.getValueType().apiValue();
            builder.append(" · ").append(valueTypeName.replace('-', ' ')).append(' ').append(promotion.getValue());
        }
        if (promotion.getCode() != null) {
            builder.append(" · ").append(promotion.getCode());
        }
        return builder.toString();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeCode(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first.trim();
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String safeApiValue(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .map(this::normalize)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(","));
    }

    private List<String> parseList(String values) {
        if (values == null || values.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(values.split(","))
                .map(this::normalize)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private record PromotionLineMatch(String productPublicId, String name, int quantity, BigDecimal unitPrice) {}
}
