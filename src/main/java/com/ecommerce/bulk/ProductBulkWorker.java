package com.ecommerce.bulk;

import com.ecommerce.media.ProductMediaStorageService;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductData;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.product.ProductRequest;
import com.ecommerce.product.StoreProductService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs product import/export jobs off the request thread. Each method is
 * {@code @Async}, so a large upload (10k+ rows) never blocks the caller or ties up
 * the web thread pool — the controller returns a job id immediately and the
 * frontend polls {@link BulkJob} for progress.
 *
 * <p>This is a separate bean from {@link ProductBulkService} on purpose: Spring's
 * {@code @Async} only applies when the method is invoked through the proxy, which
 * self-invocation inside a single bean would bypass.
 */
@Component
public class ProductBulkWorker {
    private static final Logger log = LoggerFactory.getLogger(ProductBulkWorker.class);
    private static final int PROGRESS_FLUSH_EVERY = 25;
    private static final int MAX_COLLECTED_ERRORS = 200;

    private final BulkJobRepository jobRepository;
    private final StoreProductService productService;
    private final ProductRepository productRepository;
    private final ProductCsvService csvService;
    private final ProductMediaStorageService mediaStorageService;

    public ProductBulkWorker(
            BulkJobRepository jobRepository,
            StoreProductService productService,
            ProductRepository productRepository,
            ProductCsvService csvService,
            ProductMediaStorageService mediaStorageService) {
        this.jobRepository = jobRepository;
        this.productService = productService;
        this.productRepository = productRepository;
        this.csvService = csvService;
        this.mediaStorageService = mediaStorageService;
    }

    @Async
    public void runExport(Long jobId, String storeId, ProductExportRequest request) {
        BulkJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("runExport: job {} vanished before processing", jobId);
            return;
        }
        job.setStatus(BulkJobStatus.PROCESSING);
        jobRepository.save(job);
        try {
            List<ProductData> products = productService.exportProducts(
                    storeId,
                    request.productIds(),
                    request.skus(),
                    Boolean.TRUE.equals(request.all()),
                    request.search(),
                    request.status(),
                    request.category());

            job.setTotalRows(products.size());
            job.setProcessedRows(products.size());

            byte[] csv = csvService.write(products);
            String url = mediaStorageService.uploadExportFile(storeId, "products-export.csv", "text/csv", csv);

            job.setResultUrl(url);
            job.setStatus(BulkJobStatus.COMPLETED);
            job.setMessage("Exported " + products.size() + " product" + (products.size() == 1 ? "" : "s"));
            jobRepository.save(job);
        } catch (RuntimeException ex) {
            markFailed(job, "Export failed: " + rootMessage(ex));
            log.error("runExport: job {} failed", jobId, ex);
        }
    }

    @Async
    public void runImport(Long jobId, String storeId, String ownerPublicUserId, String orgId, byte[] bytes) {
        BulkJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("runImport: job {} vanished before processing", jobId);
            return;
        }
        job.setStatus(BulkJobStatus.PROCESSING);
        jobRepository.save(job);

        List<Map<String, String>> rows;
        try {
            rows = csvService.parse(bytes);
        } catch (RuntimeException ex) {
            markFailed(job, "Could not read CSV: " + rootMessage(ex));
            log.error("runImport: job {} parse failed", jobId, ex);
            return;
        }

        if (rows.isEmpty()) {
            markFailed(job, "No data rows found. Check the CSV has a header row and at least one product.");
            return;
        }

        job.setTotalRows(rows.size());
        jobRepository.save(job);

        int created = 0;
        int updated = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            try {
                ProductRequest request = csvService.toRequest(row);
                if (request.title() == null || request.title().isBlank()) {
                    throw new IllegalArgumentException("Missing required column: title");
                }
                String sku = request.sku();
                Optional<Product> existing = (sku == null || sku.isBlank())
                        ? Optional.empty()
                        : productRepository.findByStoreIdAndSkuIgnoreCase(storeId, sku);
                if (existing.isPresent()) {
                    productService.update(storeId, orgId, existing.get().getPublicProductId(), request);
                    updated++;
                } else {
                    productService.create(storeId, ownerPublicUserId, request);
                    created++;
                }
            } catch (RuntimeException ex) {
                failed++;
                if (errors.size() < MAX_COLLECTED_ERRORS) {
                    // +2: 1 for the header row, 1 to make it 1-based like a spreadsheet
                    errors.add("Row " + (i + 2) + ": " + rootMessage(ex));
                }
            }

            job.setProcessedRows(i + 1);
            job.setCreatedCount(created);
            job.setUpdatedCount(updated);
            job.setFailedCount(failed);
            if ((i + 1) % PROGRESS_FLUSH_EVERY == 0) {
                jobRepository.save(job);
            }
        }

        if (!errors.isEmpty()) {
            job.setResultUrl(uploadErrorReport(storeId, errors));
        }
        job.setCreatedCount(created);
        job.setUpdatedCount(updated);
        job.setFailedCount(failed);
        job.setStatus((created + updated) == 0 ? BulkJobStatus.FAILED : BulkJobStatus.COMPLETED);
        job.setMessage(created + " created, " + updated + " updated, " + failed + " failed");
        jobRepository.save(job);
    }

    private String uploadErrorReport(String storeId, List<String> errors) {
        try {
            StringBuilder sb = new StringBuilder("row_error\r\n");
            for (String error : errors) {
                sb.append('"').append(error.replace("\"", "\"\"")).append('"').append("\r\n");
            }
            return mediaStorageService.uploadExportFile(
                    storeId, "import-errors.csv", "text/csv", sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException ex) {
            log.warn("uploadErrorReport failed for store {}", storeId, ex);
            return null;
        }
    }

    private void markFailed(BulkJob job, String message) {
        job.setStatus(BulkJobStatus.FAILED);
        job.setMessage(message);
        jobRepository.save(job);
    }

    private static String rootMessage(Throwable ex) {
        Throwable cursor = ex;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return (message == null || message.isBlank()) ? cursor.getClass().getSimpleName() : message;
    }
}
