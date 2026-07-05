package com.ecommerce.promotion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "store_promotions",
        uniqueConstraints = @UniqueConstraint(name = "uk_store_promotions_public_id", columnNames = "public_promotion_id"))
public class StorePromotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_promotion_id", nullable = false, unique = true, length = 36)
    private String publicPromotionId;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "code", length = 80)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 24)
    private PromotionMethod method = PromotionMethod.CODE;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 36)
    private PromotionType type = PromotionType.AMOUNT_OFF_PRODUCTS;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PromotionStatus status = PromotionStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", length = 24)
    private PromotionValueType valueType = PromotionValueType.FIXED;

    @Column(name = "promotion_value", precision = 15, scale = 2)
    private BigDecimal value = BigDecimal.ZERO;

    @Column(name = "customer_eligibility", length = 24)
    private String customerEligibility = "all";

    @Column(name = "customer_segment", length = 255)
    private String customerSegment;

    @Column(name = "customer_ids", length = 4000)
    private String customerIds;

    @Column(name = "minimum_requirement", length = 24)
    private String minimumRequirement = "none";

    @Column(name = "minimum_amount", precision = 15, scale = 2)
    private BigDecimal minimumAmount = BigDecimal.ZERO;

    @Column(name = "minimum_quantity")
    private Integer minimumQuantity;

    @Column(name = "usage_limit_mode", length = 24)
    private String usageLimitMode = "unlimited";

    @Column(name = "usage_limit_total")
    private Integer usageLimitTotal;

    @Column(name = "usage_limit_per_customer")
    private Integer usageLimitPerCustomer;

    @Column(name = "usage_count", nullable = false)
    private long usageCount;

    @Column(name = "allow_orders", nullable = false)
    private boolean allowOrders = true;

    @Column(name = "allow_products", nullable = false)
    private boolean allowProducts = true;

    @Column(name = "allow_shipping", nullable = false)
    private boolean allowShipping = true;

    @Column(name = "tags", length = 4000)
    private String tags;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "primary_target_mode", length = 24)
    private String primaryTargetMode = "products";

    @Column(name = "primary_target_ids", length = 4000)
    private String primaryTargetIds;

    @Column(name = "buy_target_mode", length = 24)
    private String buyTargetMode = "products";

    @Column(name = "buy_target_ids", length = 4000)
    private String buyTargetIds;

    @Column(name = "get_target_mode", length = 24)
    private String getTargetMode = "products";

    @Column(name = "get_target_ids", length = 4000)
    private String getTargetIds;

    @Column(name = "buy_quantity")
    private Integer buyQuantity;

    @Column(name = "get_quantity")
    private Integer getQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", length = 24)
    private PromotionRewardType rewardType = PromotionRewardType.FREE;

    @Column(name = "reward_value", precision = 15, scale = 2)
    private BigDecimal rewardValue = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicPromotionId == null || publicPromotionId.isBlank()) {
            publicPromotionId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
