package com.ecommerce.billing;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * A subscription plan tier (Basic / Growth / Professional). Prices are stored
 * per billing cycle; credits, features and target industries are attributes the
 * billing UI renders for comparison.
 */
@Getter
@Setter
@Entity
@Table(
        name = "billing_plans",
        uniqueConstraints = @UniqueConstraint(name = "uk_billing_plans_code", columnNames = "code"))
public class Plan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "tagline", length = 255)
    private String tagline;

    @Column(name = "monthly_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyPrice = BigDecimal.ZERO;

    @Column(name = "yearly_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal yearlyPrice = BigDecimal.ZERO;

    @Column(name = "currency_code", nullable = false, length = 8)
    private String currencyCode = "INR";

    @Column(name = "currency_symbol", nullable = false, length = 8)
    private String currencySymbol = "₹";

    /** Credit points included with the plan each cycle (SMS/email/AI usage, etc.). */
    @Column(name = "credits", nullable = false)
    private int credits;

    @Column(name = "highlighted", nullable = false)
    private boolean highlighted;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "billing_plan_features", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "position")
    @Column(name = "feature", length = 255)
    private List<String> features = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "billing_plan_industries", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "position")
    @Column(name = "industry", length = 120)
    private List<String> industries = new ArrayList<>();
}
