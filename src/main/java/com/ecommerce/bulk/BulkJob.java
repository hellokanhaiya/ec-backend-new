package com.ecommerce.bulk;

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

/**
 * Tracks a long-running product import or export. Persisted so progress survives
 * restarts and can be polled by the frontend while a background worker processes
 * the rows. The table is created automatically by Hibernate (ddl-auto: update).
 */
@Entity
@Table(
        name = "bulk_job",
        indexes = {
            @Index(name = "idx_bulk_job_public_id", columnList = "public_job_id"),
            @Index(name = "idx_bulk_job_store", columnList = "store_id")
        })
public class BulkJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_job_id", nullable = false, unique = true, length = 36)
    private String publicJobId;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", length = 36)
    private String ownerPublicUserId;

    @Column(name = "org_id", length = 36)
    private String orgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private BulkJobType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BulkJobStatus status = BulkJobStatus.PENDING;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "processed_rows", nullable = false)
    private int processedRows;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    /** For exports: the downloadable CSV URL. For imports with errors: the error-report CSV URL. */
    @Column(name = "result_url", length = 1024)
    private String resultUrl;

    @Column(name = "message", length = 1024)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicJobId == null || publicJobId.isBlank()) {
            publicJobId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getPublicJobId() {
        return publicJobId;
    }

    public void setPublicJobId(String publicJobId) {
        this.publicJobId = publicJobId;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getOwnerPublicUserId() {
        return ownerPublicUserId;
    }

    public void setOwnerPublicUserId(String ownerPublicUserId) {
        this.ownerPublicUserId = ownerPublicUserId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public BulkJobType getType() {
        return type;
    }

    public void setType(BulkJobType type) {
        this.type = type;
    }

    public BulkJobStatus getStatus() {
        return status;
    }

    public void setStatus(BulkJobStatus status) {
        this.status = status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getProcessedRows() {
        return processedRows;
    }

    public void setProcessedRows(int processedRows) {
        this.processedRows = processedRows;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(int createdCount) {
        this.createdCount = createdCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public String getResultUrl() {
        return resultUrl;
    }

    public void setResultUrl(String resultUrl) {
        this.resultUrl = resultUrl;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
