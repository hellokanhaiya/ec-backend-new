package com.ecommerce.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A registered plugin/app. In v1 every app is store-private and auto-created alongside its API
 * token; the entity exists so a future marketplace (developer-owned global apps + per-store
 * installs) needs no remodel.
 */
@Getter
@Setter
@Entity
@Table(
        name = "plugin_apps",
        uniqueConstraints = @UniqueConstraint(name = "uk_plugin_apps_public_id", columnNames = "public_app_id"),
        indexes = @Index(name = "idx_plugin_apps_store", columnList = "store_id"))
public class PluginApp {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_app_id", nullable = false, unique = true, length = 36)
    private String publicAppId;

    // Null once marketplace apps become developer-owned/global; always set for v1 private apps.
    @Column(name = "store_id", length = 36)
    private String storeId;

    @Column(name = "org_id", length = 36)
    private String orgId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    // Base URL of the developer-hosted plugin service; iframe pages and direct actions resolve
    // against it. Null for plain API-token apps that declare no UI extensions.
    @Column(name = "app_url", length = 500)
    private String appUrl;

    @Column(name = "manifest_url", length = 500)
    private String manifestUrl;

    // The validated manifest exactly as last fetched; the extensions feed is served from this.
    @Lob
    @Column(name = "manifest_json", columnDefinition = "LONGTEXT")
    private String manifestJson;

    @Column(name = "manifest_version", length = 40)
    private String manifestVersion;

    // Shared secret between platform and this app: signs context JWTs handed to the plugin's
    // iframe and HMACs direct-action payloads. Shown once at registration, stored raw because
    // the platform must sign with it (unlike token hashes).
    @Column(name = "signing_secret", length = 100)
    private String signingSecret;

    // Dev-mode apps may use localhost/private manifest + app URLs; production apps may not.
    @Column(name = "dev_mode", nullable = false)
    private boolean devMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (publicAppId == null || publicAppId.isBlank()) {
            publicAppId = UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
