package com.ecommerce.customer;

import java.util.List;

/** Outcome of a CSV import: totals plus a per-row error list the UI can display. */
public record ImportResultData(
        int total,
        int created,
        int updated,
        int skipped,
        int failed,
        List<ImportError> errors) {

    public record ImportError(int rowNumber, String identifier, String message) {}
}
