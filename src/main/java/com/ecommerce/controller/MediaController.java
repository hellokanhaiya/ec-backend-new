package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.media.MediaData;
import com.ecommerce.media.MediaKeepRequest;
import com.ecommerce.media.MediaListData;
import com.ecommerce.media.ProductMediaStorageService;
import com.ecommerce.media.ProductMediaUploadData;
import com.ecommerce.media.StoreMediaService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class MediaController {
    private final StoreMediaService mediaService;
    private final ProductMediaStorageService storageService;
    private final AccessControlService accessControl;

    public MediaController(
            StoreMediaService mediaService,
            ProductMediaStorageService storageService,
            AccessControlService accessControl) {
        this.mediaService = mediaService;
        this.storageService = storageService;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/media")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "date") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String mediaType,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String productSearch,
            @RequestParam(required = false) Long minSize,
            @RequestParam(required = false) Long maxSize,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_MEDIA, AccessLevel.VIEW);
        MediaListData data = mediaService.list(
                scope.storeId(),
                search,
                sort,
                sortDir,
                mediaType,
                format,
                source,
                category,
                productSearch,
                minSize,
                maxSize,
                dateFrom,
                dateTo,
                page,
                size);
        return ok("Media loaded", data);
    }

    @GetMapping("/{audience}/auth/media/{publicMediaId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String audience,
            @PathVariable String publicMediaId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_MEDIA, AccessLevel.VIEW);
        MediaData data = mediaService.get(scope.storeId(), publicMediaId);
        return ok("Media loaded", data);
    }

    @PostMapping(value = "/{audience}/auth/media/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("files") List<MultipartFile> files) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_MEDIA, AccessLevel.MANAGE);
        List<ProductMediaUploadData> uploaded = storageService.uploadProductImages(scope.storeId(), files);
        List<MediaData> persisted = new ArrayList<>();
        for (ProductMediaUploadData item : uploaded) {
            persisted.add(mediaService.persist(scope.storeId(), item, "upload"));
        }
        return ok("Media uploaded", persisted);
    }

    @PostMapping("/{audience}/auth/media/keep")
    public ResponseEntity<Map<String, Object>> keep(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody MediaKeepRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_MEDIA, AccessLevel.MANAGE);
        if (request == null || request.url() == null || request.url().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image URL is required");
        }
        ProductMediaUploadData uploaded = storageService.uploadFromUrl(scope.storeId(), request.url());
        String label = request.prompt() != null && !request.prompt().isBlank()
                ? request.prompt().trim()
                : null;
        MediaData data = mediaService.persistWithLabel(scope.storeId(), uploaded, "ai", label);
        return ok("AI image saved to library", data);
    }

    @DeleteMapping("/{audience}/auth/media/{publicMediaId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String audience,
            @PathVariable String publicMediaId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_MEDIA, AccessLevel.MANAGE);
        mediaService.delete(scope.storeId(), publicMediaId);
        return ok("Media deleted", null);
    }

    private StoreScope resolveScope(String authorization, String audience, String permissionKey, AccessLevel required) {
        StoreAccessScope scope = accessControl.requireScope(authorization, audience, permissionKey, required);
        return new StoreScope(scope.storeId(), scope.orgId());
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    private record StoreScope(String storeId, String orgId) {}
}
