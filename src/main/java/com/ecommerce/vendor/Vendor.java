package com.ecommerce.vendor;

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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A marketplace vendor (seller) who owns products sold through the store and
 * earns the sale value minus the store's commission. Store-scoped exactly like
 * {@link com.ecommerce.purchase.PurchaseSupplier}.
 */
@Getter
@Setter
@Entity
@Table(
        name = "vendors",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_vendors_public_id", columnNames = "public_vendor_id"))
public class Vendor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_vendor_id", nullable = false, unique = true, length = 36)
    private String publicVendorId;

    @Column(name = "vendor_code", length = 40)
    private String vendorCode;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    /** Once a vendor self-service login exists, the linked admin user id. */
    @Column(name = "admin_user_public_id", length = 36)
    private String adminUserPublicId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "company", length = 255)
    private String company;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 48)
    private String phone;

    @Column(name = "logo_url", length = 1024)
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private VendorStatus status = VendorStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type", nullable = false, length = 24)
    private CommissionType commissionType = CommissionType.PERCENTAGE;

    /** Percentage (0–100) or flat amount, per {@link #commissionType}. */
    @Column(name = "commission_rate", nullable = false, precision = 15, scale = 2)
    private BigDecimal commissionRate = BigDecimal.ZERO;

    // --- Payout details (bank/UPI kept light for now) -----------------------
    @Column(name = "payout_account_name", length = 255)
    private String payoutAccountName;

    @Column(name = "payout_account_number", length = 64)
    private String payoutAccountNumber;

    @Column(name = "payout_ifsc", length = 32)
    private String payoutIfsc;

    @Column(name = "payout_upi", length = 128)
    private String payoutUpi;

    // --- Address ------------------------------------------------------------
    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "state", length = 128)
    private String state;

    @Column(name = "pincode", length = 32)
    private String pincode;

    @Column(name = "country", length = 80)
    private String country;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicVendorId == null || publicVendorId.isBlank()) {
            publicVendorId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
