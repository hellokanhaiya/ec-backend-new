package com.ecommerce.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A scoped plugin API token. Only the SHA-256 hash of the token is stored — the plaintext
 * ({@code sk_plg_…}) is shown exactly once at creation time.
 */
@Getter
@Setter
@Entity
@Table(
        name = "plugin_api_tokens",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_plugin_tokens_public_id", columnNames = "public_token_id"),
            @UniqueConstraint(name = "uk_plugin_tokens_hash", columnNames = "token_hash")
        },
        indexes = @Index(name = "idx_plugin_tokens_store", columnList = "store_id"))
public class PluginApiToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_token_id", nullable = false, unique = true, length = 36)
    private String publicTokenId;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "org_id", length = 36)
    private String orgId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "token_prefix", nullable = false, length = 16)
    private String tokenPrefix;

    @Column(name = "last_four", nullable = false, length = 4)
    private String lastFour;

    // Comma-joined scope keys, each validated against PluginScopeCatalog at creation.
    @Column(name = "scopes", nullable = false, length = 1000)
    private String scopes;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (publicTokenId == null || publicTokenId.isBlank()) {
            publicTokenId = UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
