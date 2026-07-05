package com.ecommerce.order;

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
@Table(name = "pdf_templates")
public class PdfTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_template_id", nullable = false, unique = true, length = 36)
    private String publicTemplateId;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 24)
    private PdfTemplateType type = PdfTemplateType.INVOICE;

    @Column(name = "is_default", nullable = false)
    private boolean defaultTemplate = false;

    @Column(name = "is_system", nullable = false)
    private boolean systemTemplate = false;

    @Column(name = "layout_config", columnDefinition = "TEXT")
    private String layoutConfig;

    @Column(name = "header_html", columnDefinition = "TEXT")
    private String headerHtml;

    @Column(name = "footer_html", columnDefinition = "TEXT")
    private String footerHtml;

    @Column(name = "logo_url", length = 1000)
    private String logoUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicTemplateId == null || publicTemplateId.isBlank()) {
            publicTemplateId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
