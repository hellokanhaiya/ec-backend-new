package com.ecommerce.store;

import com.ecommerce.auth.AuthAudience;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "store_profiles")
public class StoreProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_user_id", nullable = false, unique = true, length = 36)
    private String publicUserId;

    @Column(name = "org_id", nullable = false, unique = true, length = 36)
    private String orgId;

    @Column(name = "store_id", nullable = false, unique = true, length = 36)
    private String storeId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "owner_public_user_id", nullable = false, unique = true, length = 36)
    private String ownerPublicUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AuthAudience audience;

    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "admin_email", length = 255)
    private String adminEmail;

    @Column(name = "admin_phone", length = 64)
    private String adminPhone;

    @Column(name = "category_key", nullable = false, length = 64)
    private String categoryKey;

    @Column(name = "category_label", nullable = false, length = 128)
    private String categoryLabel;

    @Column(name = "custom_category", length = 255)
    private String customCategory;

    @Column(name = "currency_code", nullable = false, length = 8)
    private String currencyCode;

    @Column(name = "country_code", nullable = false, length = 8)
    private String countryCode;

    @Column(name = "country_name", nullable = false, length = 128)
    private String countryName;

    @Column(name = "tax_number", length = 64)
    private String taxNumber;

    @Column(name = "license_key", length = 128)
    private String licenseKey;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "state", length = 128)
    private String state;

    @Column(name = "postal_code", length = 32)
    private String postalCode;

    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @Column(name = "date_format", length = 32)
    private String dateFormat;

    @Column(name = "business_email", length = 255)
    private String businessEmail;

    @Column(name = "business_phone", length = 64)
    private String businessPhone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicUserId == null || publicUserId.isBlank()) {
            publicUserId = UUID.randomUUID().toString();
        }
        if (orgId == null || orgId.isBlank()) {
            orgId = UUID.randomUUID().toString();
        }
        if (storeId == null || storeId.isBlank()) {
            storeId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
