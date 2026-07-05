package com.ecommerce.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * A person's membership in a store with an assigned role. Created ACTIVE for the owner, or INVITED
 * (email only, {@code adminUserId} null) for an invite that links to an admin user on first sign-in.
 */
@Getter
@Setter
@Entity
@Table(name = "store_members", indexes = {
        @Index(name = "idx_store_members_store", columnList = "store_id"),
        @Index(name = "idx_store_members_admin_user", columnList = "admin_user_id"),
        @Index(name = "idx_store_members_email", columnList = "email")})
public class StoreMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    /** Null until an invited member signs in and gets linked. */
    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(length = 255)
    private String email;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(length = 128)
    private String title;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MemberStatus status;

    @Column(name = "invited_at")
    private Instant invitedAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

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
