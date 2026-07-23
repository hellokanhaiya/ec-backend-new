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
import lombok.Getter;
import lombok.Setter;

/**
 * Plugin-owned key/value data attached to a store resource (product, order) or to the app itself
 * (settings). The namespace is always the owning app's public id and is derived from the calling
 * token — never from request input — so one app can never read or write another app's data.
 */
@Getter
@Setter
@Entity
@Table(
        name = "plugin_metafields",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_plugin_metafields_entry",
                columnNames = {"store_id", "namespace", "resource_type", "resource_id", "mkey"}),
        indexes = @Index(name = "idx_plugin_metafields_lookup", columnList = "store_id,resource_type,mkey"))
public class PluginMetafield {
    public static final String RESOURCE_PRODUCT = "product";
    public static final String RESOURCE_ORDER = "order";
    public static final String RESOURCE_APP = "app";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "namespace", nullable = false, length = 36)
    private String namespace;

    @Column(name = "resource_type", nullable = false, length = 20)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, length = 64)
    private String resourceId;

    // "mkey" because "key" is a reserved word in MySQL.
    @Column(name = "mkey", nullable = false, length = 120)
    private String mkey;

    @Column(name = "value_json", columnDefinition = "TEXT")
    private String valueJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
