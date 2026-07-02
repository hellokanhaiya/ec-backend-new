package com.ecommerce.customer;

import com.ecommerce.tag.Tag;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A store customer (someone who buys from the store) — distinct from
 * {@code ConsumerAuthUser}, which is platform-login auth. Scoped to a store via
 * {@code storeId}. Loyalty balances (wallet / reward points / store credit) are
 * editable here; order-derived metrics (total orders / spent / LTV) come from the
 * orders module later.
 */
@Getter
@Setter
@Entity
@Table(
        name = "customers",
        uniqueConstraints = @UniqueConstraint(name = "uk_customers_public_id", columnNames = "public_customer_id"))
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_customer_id", nullable = false, unique = true, length = 36)
    private String publicCustomerId;

    @Column(name = "customer_code", length = 32)
    private String customerCode;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    @Column(name = "first_name", nullable = false, length = 128)
    private String firstName;

    @Column(name = "last_name", length = 128)
    private String lastName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone_country_code", length = 8)
    private String phoneCountryCode;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "accepts_email", nullable = false)
    private boolean acceptsEmail = false;

    @Column(name = "accepts_sms", nullable = false)
    private boolean acceptsSms = false;

    @Column(name = "accepts_whatsapp", nullable = false)
    private boolean acceptsWhatsapp = false;

    @Column(name = "accepts_promos", nullable = false)
    private boolean acceptsPromos = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(name = "wallet", precision = 15, scale = 2)
    private BigDecimal wallet = BigDecimal.ZERO;

    @Column(name = "reward_points")
    private Integer rewardPoints = 0;

    @Column(name = "store_credit", precision = 15, scale = 2)
    private BigDecimal storeCredit = BigDecimal.ZERO;

    @Column(name = "notes", length = 2000)
    private String notes;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private List<CustomerAddress> addresses = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "customer_tags",
            joinColumns = @JoinColumn(name = "customer_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicCustomerId == null || publicCustomerId.isBlank()) {
            publicCustomerId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
