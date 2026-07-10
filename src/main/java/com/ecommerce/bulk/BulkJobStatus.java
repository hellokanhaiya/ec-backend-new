package com.ecommerce.bulk;

/** Lifecycle of a bulk import/export job. The frontend polls until it reaches a
 * terminal state (COMPLETED or FAILED). */
public enum BulkJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
