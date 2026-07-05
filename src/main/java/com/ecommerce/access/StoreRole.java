package com.ecommerce.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** A role within a store. Its page-by-page grants live in {@link StoreRolePermission}. */
@Getter
@Setter
@Entity
@Table(name = "store_roles", indexes = {@Index(name = "idx_store_roles_store", columnList = "store_id")})
public class StoreRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    /** Stable slug (e.g. "owner", "manager", or a generated slug for custom roles). */
    @Column(name = "role_key", nullable = false, length = 64)
    private String roleKey;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 255)
    private String description;

    /** True for the five built-in templates seeded on store creation. */
    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    /** True for roles that cannot be edited or deleted (the Owner role). */
    @Column(nullable = false)
    private boolean locked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicId == null || publicId.isBlank()) {
            publicId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
