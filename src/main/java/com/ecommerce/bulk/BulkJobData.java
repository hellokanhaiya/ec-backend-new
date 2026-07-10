package com.ecommerce.bulk;

import java.time.Instant;

/** Status payload the frontend polls while a bulk job runs. */
public record BulkJobData(
        String jobId,
        String type,
        String status,
        int totalRows,
        int processedRows,
        int createdCount,
        int updatedCount,
        int failedCount,
        String resultUrl,
        String message,
        Instant createdAt,
        Instant updatedAt) {

    public static BulkJobData from(BulkJob job) {
        return new BulkJobData(
                job.getPublicJobId(),
                job.getType().name(),
                job.getStatus().name(),
                job.getTotalRows(),
                job.getProcessedRows(),
                job.getCreatedCount(),
                job.getUpdatedCount(),
                job.getFailedCount(),
                job.getResultUrl(),
                job.getMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}
