package com.ecommerce.shipping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A shipping profile groups products that share the same delivery zones and rates
 * (Shopify's "shipping profiles"). Every store has a default "General profile"; products
 * reference a profile via {@code Product.shippingProfilePublicId} (null = default).
 */
@Getter
@Setter
@Entity
@Table(
        name = "shipping_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_shipping_profiles_public_id", columnNames = "public_profile_id"))
public class ShippingProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_profile_id", nullable = false, unique = true, length = 36)
    private String publicProfileId;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean defaultProfile = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicProfileId == null || publicProfileId.isBlank()) {
            publicProfileId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
