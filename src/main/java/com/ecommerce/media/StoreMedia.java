package com.ecommerce.media;

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

@Getter
@Setter
@Entity
@Table(
        name = "store_media",
        uniqueConstraints = @UniqueConstraint(name = "uk_store_media_public_id", columnNames = "public_media_id"))
public class StoreMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_media_id", nullable = false, unique = true, length = 36)
    private String publicMediaId;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "url", nullable = false, length = 1024)
    private String url;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size", nullable = false)
    private long size;

    @Column(name = "object_name", nullable = false, length = 1024)
    private String objectName;

    @Column(name = "label", length = 255)
    private String label;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicMediaId == null || publicMediaId.isBlank()) {
            publicMediaId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
