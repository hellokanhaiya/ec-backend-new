package com.ecommerce.bulk;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Creates and looks up bulk product jobs. Intentionally NOT {@code @Transactional}:
 * the job row must be committed before the async worker is dispatched, otherwise the
 * worker's fresh transaction could read before the insert is visible.
 */
@Service
public class ProductBulkService {
    private static final long MAX_IMPORT_BYTES = 25L * 1024L * 1024L;

    private final BulkJobRepository jobRepository;
    private final ProductBulkWorker worker;

    public ProductBulkService(BulkJobRepository jobRepository, ProductBulkWorker worker) {
        this.jobRepository = jobRepository;
        this.worker = worker;
    }

    public BulkJobData startExport(
            String storeId, String ownerPublicUserId, String orgId, ProductExportRequest request) {
        ProductExportRequest safe = request == null
                ? new ProductExportRequest(null, null, Boolean.FALSE, null, null, null)
                : request;
        BulkJob job = newJob(storeId, ownerPublicUserId, orgId, BulkJobType.EXPORT);
        job = jobRepository.save(job);
        worker.runExport(job.getId(), storeId, safe);
        return BulkJobData.from(job);
    }

    public BulkJobData startImport(String storeId, String ownerPublicUserId, String orgId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "A CSV file is required");
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new ResponseStatusException(BAD_REQUEST, "CSV must be 25MB or smaller");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not read the uploaded file", ex);
        }
        BulkJob job = newJob(storeId, ownerPublicUserId, orgId, BulkJobType.IMPORT);
        job = jobRepository.save(job);
        worker.runImport(job.getId(), storeId, ownerPublicUserId, orgId, bytes);
        return BulkJobData.from(job);
    }

    public BulkJobData getJob(String storeId, String publicJobId) {
        BulkJob job = jobRepository
                .findByStoreIdAndPublicJobId(storeId, publicJobId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Job not found"));
        return BulkJobData.from(job);
    }

    private BulkJob newJob(String storeId, String ownerPublicUserId, String orgId, BulkJobType type) {
        BulkJob job = new BulkJob();
        job.setStoreId(storeId);
        job.setOwnerPublicUserId(ownerPublicUserId);
        job.setOrgId(orgId);
        job.setType(type);
        job.setStatus(BulkJobStatus.PENDING);
        return job;
    }
}
