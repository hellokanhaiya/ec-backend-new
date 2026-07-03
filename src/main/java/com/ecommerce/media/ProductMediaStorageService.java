package com.ecommerce.media;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductMediaStorageService {
    private static final Logger log = LoggerFactory.getLogger(ProductMediaStorageService.class);
    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String publicBucket;
    private final String publicBaseUrl;
    private final String projectId;
    private final String credentialsLocation;
    private final ResourceLoader resourceLoader;
    private volatile Storage storage;

    public ProductMediaStorageService(
            @Value("${app.storage.google.public-bucket:shy-pub}") String publicBucket,
            @Value("${app.storage.public-base-url:https://storage.googleapis.com}") String publicBaseUrl,
            @Value("${app.storage.google.project-id:}") String projectId,
            @Value("${app.storage.google.credentials-location:}") String credentialsLocation,
            ResourceLoader resourceLoader) {
        this.publicBucket = publicBucket;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.projectId = blankToNull(projectId);
        this.credentialsLocation = blankToNull(credentialsLocation);
        this.resourceLoader = resourceLoader;
    }

    public List<ProductMediaUploadData> uploadProductImages(String storageFolderId, List<MultipartFile> files) {
        String cleanStorageFolderId = requireStorageFolderId(storageFolderId);
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Choose at least one image to upload");
        }

        List<ProductMediaUploadData> uploaded = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            uploaded.add(uploadSingleImage(cleanStorageFolderId, file));
        }

        if (uploaded.isEmpty()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Choose at least one non-empty image to upload. If you are testing with curl, use -F \"files=@C:\\path\\image.jpg\" instead of --data-raw.");
        }
        return uploaded;
    }

    public ProductMediaUploadData uploadFromUrl(String storageFolderId, String imageUrl) {
        String cleanStorageFolderId = requireStorageFolderId(storageFolderId);
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Image URL is required");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl.trim()))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(BAD_REQUEST, "Unable to fetch image from URL (HTTP " + response.statusCode() + ")");
            }

            byte[] bytes = response.body();
            if (bytes == null || bytes.length == 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Image URL returned empty content");
            }
            if (bytes.length > MAX_IMAGE_BYTES) {
                throw new ResponseStatusException(BAD_REQUEST, "Image from URL exceeds 10MB limit");
            }

            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .map(ct -> ct.split(";")[0].trim().toLowerCase(Locale.ROOT))
                    .orElse("image/png");
            if (!contentType.startsWith("image/")) {
                contentType = "image/png";
            }

            String safeFileName = safeCloudFileName("ai-generated", contentType);
            String objectName = cleanStorageFolderId + "/" + safeFileName;
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(publicBucket, objectName))
                    .setContentType(contentType)
                    .build();

            storage().create(blobInfo, bytes);

            return new ProductMediaUploadData(
                    publicBaseUrl + "/" + publicBucket + "/" + objectName,
                    safeFileName,
                    contentType,
                    bytes.length,
                    objectName);

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Image download was interrupted", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Unable to download and upload image: " + rootErrorMessage(ex), ex);
        }
    }

    @Async
    public void deleteProductImagesAsync(String storageFolderId, Set<String> urls) {
        deleteProductImagesAsync(storageFolderId, null, urls);
    }

    @Async
    public void deleteProductImagesAsync(String storageFolderId, String legacyStorageFolderId, Set<String> urls) {
        Set<String> storageFolderIds = storageFolderIds(storageFolderId, legacyStorageFolderId);
        if (urls == null || urls.isEmpty()) {
            return;
        }

        Set<String> objectNames = new LinkedHashSet<>();
        for (String url : urls) {
            objectNameFromUrl(storageFolderIds, url).ifPresent(objectNames::add);
        }

        for (String objectName : objectNames) {
            try {
                boolean deleted = storage().delete(BlobId.of(publicBucket, objectName));
                if (!deleted) {
                    log.debug("Cloud media already absent: {}", objectName);
                }
            } catch (RuntimeException ex) {
                log.warn("Cloud media cleanup failed for {}", objectName, ex);
            }
        }
    }

    private ProductMediaUploadData uploadSingleImage(String storageFolderId, MultipartFile file) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")) {
            throw new ResponseStatusException(BAD_REQUEST, "Only image uploads are supported");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(BAD_REQUEST, "Image must be 10MB or smaller");
        }

        String safeFileName = safeCloudFileName(file.getOriginalFilename(), contentType);
        String objectName = storageFolderId + "/" + safeFileName;
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(publicBucket, objectName))
                .setContentType(contentType)
                .build();

        try {
            storage().create(blobInfo, file.getBytes());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (StorageException ex) {
            throw new ResponseStatusException(
                    INTERNAL_SERVER_ERROR,
                    "Unable to upload image to cloud storage: " + storageErrorMessage(ex),
                    ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Unable to read uploaded image data", ex);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                    INTERNAL_SERVER_ERROR,
                    "Unable to upload image to cloud storage: " + rootErrorMessage(ex),
                    ex);
        }

        return new ProductMediaUploadData(
                publicBaseUrl + "/" + publicBucket + "/" + objectName,
                safeFileName,
                contentType,
                file.getSize(),
                objectName);
    }

    private Optional<String> objectNameFromUrl(Set<String> storageFolderIds, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }

        String url = rawUrl.trim();
        String prefix = publicBaseUrl + "/" + publicBucket + "/";
        String objectName = null;
        if (url.startsWith(prefix)) {
            objectName = url.substring(prefix.length());
        } else {
            try {
                URI uri = URI.create(url);
                String path = uri.getPath();
                String bucketPathPrefix = "/" + publicBucket + "/";
                if (path != null && path.startsWith(bucketPathPrefix)) {
                    objectName = path.substring(bucketPathPrefix.length());
                }
            } catch (IllegalArgumentException ex) {
                log.debug("Skipping non-cloud media url: {}", url);
            }
        }

        if (objectName == null || !belongsToAnyStorageFolder(objectName, storageFolderIds)) {
            return Optional.empty();
        }
        return Optional.of(objectName);
    }

    private Storage storage() {
        Storage current = storage;
        if (current == null) {
            synchronized (this) {
                current = storage;
                if (current == null) {
                    try {
                        current = createStorageClient();
                    } catch (RuntimeException ex) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Cloud storage is not configured. Set GOOGLE_APPLICATION_CREDENTIALS to a Google service-account JSON with access to the public bucket.",
                                ex);
                    }
                    storage = current;
                }
            }
        }
        return current;
    }

    private Storage createStorageClient() {
        StorageOptions.Builder builder = StorageOptions.newBuilder();
        if (projectId != null) {
            builder.setProjectId(projectId);
        }
        if (credentialsLocation != null) {
            try {
                builder.setCredentials(loadCredentials(credentialsLocation));
            } catch (IOException ex) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unable to read Google storage credentials from " + credentialsLocation,
                        ex);
            }
        }
        return builder.build().getService();
    }

    private GoogleCredentials loadCredentials(String location) throws IOException {
        String cleanLocation = location.trim();
        if (cleanLocation.startsWith("classpath:") || cleanLocation.startsWith("file:")) {
            Resource resource = resourceLoader.getResource(cleanLocation);
            try (InputStream inputStream = resource.getInputStream()) {
                return GoogleCredentials.fromStream(inputStream);
            }
        }

        Path credentialsPath = Path.of(cleanLocation);
        if (Files.exists(credentialsPath)) {
            try (InputStream inputStream = Files.newInputStream(credentialsPath)) {
                return GoogleCredentials.fromStream(inputStream);
            }
        }

        Resource resource = resourceLoader.getResource(cleanLocation);
        try (InputStream inputStream = resource.getInputStream()) {
            return GoogleCredentials.fromStream(inputStream);
        }
    }

    private static String requireStorageFolderId(String storageFolderId) {
        if (storageFolderId == null || storageFolderId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Store id is required before uploading media");
        }
        return storageFolderId.trim();
    }

    private static Set<String> storageFolderIds(String primaryStorageFolderId, String legacyStorageFolderId) {
        Set<String> folderIds = new LinkedHashSet<>();
        addStorageFolderId(folderIds, primaryStorageFolderId);
        addStorageFolderId(folderIds, legacyStorageFolderId);
        if (folderIds.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Store id is required before cleaning up media");
        }
        return folderIds;
    }

    private static void addStorageFolderId(Set<String> folderIds, String storageFolderId) {
        if (storageFolderId != null && !storageFolderId.isBlank()) {
            folderIds.add(storageFolderId.trim());
        }
    }

    private static boolean belongsToAnyStorageFolder(String objectName, Set<String> storageFolderIds) {
        for (String storageFolderId : storageFolderIds) {
            if (objectName.startsWith(storageFolderId + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String storageErrorMessage(StorageException exception) {
        String message = exception.getMessage();
        String cleanMessage = message == null || message.isBlank() ? "Google Cloud rejected the request" : message;
        int code = exception.getCode();
        return code > 0 ? "Google Cloud returned " + code + ". " + cleanMessage : cleanMessage;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String rootErrorMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static String safeCloudFileName(String originalName, String contentType) {
        String fileName = originalName == null || originalName.isBlank() ? "image" : originalName.trim();
        fileName = fileName.replace("\\", "/");
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }

        String extension = extension(fileName).orElseGet(() -> extensionFromContentType(contentType));
        String baseName = extension.isBlank() ? fileName : fileName.substring(0, fileName.length() - extension.length());
        baseName = baseName
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("(^-+|-+$)", "")
                .toLowerCase(Locale.ROOT);
        if (baseName.isBlank()) {
            baseName = "image";
        }

        return baseName + "-" + Instant.now().toEpochMilli() + "-"
                + ThreadLocalRandom.current().nextInt(1000, 9999) + extension;
    }

    private static Optional<String> extension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == fileName.length() - 1) {
            return Optional.empty();
        }
        String ext = fileName.substring(lastDot).replaceAll("[^A-Za-z0-9.]+", "").toLowerCase(Locale.ROOT);
        return ext.length() > 12 ? Optional.empty() : Optional.of(ext);
    }

    private static String extensionFromContentType(String contentType) {
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/svg+xml" -> ".svg";
            default -> ".png";
        };
    }

    private static String trimTrailingSlash(String value) {
        String clean = value == null || value.isBlank() ? "https://storage.googleapis.com" : value.trim();
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }
}
