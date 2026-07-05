package com.ecommerce.controller;

import com.ecommerce.auth.AuthAudience;
import com.ecommerce.auth.CurrentAccountData;
import com.ecommerce.auth.CurrentAccountService;
import com.ecommerce.media.ProductMediaStorageService;
import com.ecommerce.media.ProductMediaUploadData;
import com.ecommerce.media.StoreMediaService;
import com.ecommerce.product.GenerateDescriptionData;
import com.ecommerce.product.GenerateDescriptionRequest;
import com.ecommerce.product.GenerateImageData;
import com.ecommerce.product.GenerateImageRequest;
import com.ecommerce.product.ProductAiService;
import com.ecommerce.product.ProductData;
import com.ecommerce.product.ProductListData;
import com.ecommerce.product.ProductOverviewData;
import com.ecommerce.product.ProductPickerListData;
import com.ecommerce.product.ProductRequest;
import com.ecommerce.product.StoreProductService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class ProductController {
    private final StoreProductService productService;
    private final ProductAiService productAiService;
    private final ProductMediaStorageService productMediaStorageService;
    private final StoreMediaService storeMediaService;
    private final CurrentAccountService currentAccountService;

    public ProductController(
            StoreProductService productService,
            ProductAiService productAiService,
            ProductMediaStorageService productMediaStorageService,
            StoreMediaService storeMediaService,
            CurrentAccountService currentAccountService) {
        this.productService = productService;
        this.productAiService = productAiService;
        this.productMediaStorageService = productMediaStorageService;
        this.storeMediaService = storeMediaService;
        this.currentAccountService = currentAccountService;
    }

    @GetMapping("/{audience}/auth/products")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "0") int size) {
        StoreScope scope = resolveScope(authorization, audience);
        ProductListData data =
                productService.list(scope.storeId(), search, status, category, dateFrom, dateTo, page, size);
        return ok("Products loaded", data);
    }

    @GetMapping("/{audience}/auth/products/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience);
        ProductOverviewData data = productService.overview(scope.storeId());
        return ok("Product overview loaded", data);
    }

    @GetMapping("/{audience}/auth/products/redirects")
    public ResponseEntity<Map<String, Object>> redirects(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience);
        return ok("Product redirects loaded", productService.redirects(scope.storeId()));
    }

    @GetMapping("/{audience}/auth/products/browse")
    public ResponseEntity<Map<String, Object>> browse(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        StoreScope scope = resolveScope(authorization, audience);
        ProductPickerListData data = productService.picker(scope.storeId(), search, page, size);
        return ok("Products loaded", data);
    }

    @GetMapping("/{audience}/auth/products/{publicProductId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String audience,
            @PathVariable String publicProductId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience);
        ProductData data = productService.get(scope.storeId(), publicProductId);
        return ok("Product loaded", data);
    }

    @PostMapping("/{audience}/auth/products")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ProductRequest request) {
        StoreScope scope = resolveScope(authorization, audience);
        ProductData data = productService.create(scope.storeId(), scope.ownerPublicUserId(), request);
        return ok("Product created", data);
    }

    @PostMapping(value = "/{audience}/auth/products/media")
    public ResponseEntity<Map<String, Object>> uploadMedia(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("files") List<MultipartFile> files) {
        StoreScope scope = resolveScope(authorization, audience);
        List<ProductMediaUploadData> data = productMediaStorageService.uploadProductImages(scope.storeId(), files);
        for (ProductMediaUploadData item : data) {
            storeMediaService.persist(scope.storeId(), item, "upload");
        }
        return ok("Product media uploaded", data);
    }

    @PutMapping("/{audience}/auth/products/{publicProductId}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String audience,
            @PathVariable String publicProductId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ProductRequest request) {
        StoreScope scope = resolveScope(authorization, audience);
        ProductData data = productService.update(scope.storeId(), scope.orgId(), publicProductId, request);
        return ok("Product updated", data);
    }

    @DeleteMapping("/{audience}/auth/products/{publicProductId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String audience,
            @PathVariable String publicProductId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience);
        productService.delete(scope.storeId(), scope.orgId(), publicProductId);
        return ok("Product deleted", null);
    }

    @PostMapping("/{audience}/auth/products/generate-description")
    public ResponseEntity<Map<String, Object>> generateDescription(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody GenerateDescriptionRequest request) {
        resolveScope(authorization, audience);
        GenerateDescriptionData data = productAiService.generateDescription(request);
        return ok("Description generated", data);
    }

    @PostMapping("/{audience}/auth/products/generate-image")
    public ResponseEntity<Map<String, Object>> generateImage(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody GenerateImageRequest request) {
        resolveScope(authorization, audience);
        GenerateImageData data = productAiService.generateImage(request);
        return ok("Image generated", data);
    }

    private StoreScope resolveScope(String authorization, String audience) {
        CurrentAccountData currentAccount = currentAccountService.resolveCurrentAccount(authorization);
        if (currentAccount.audience() != AuthAudience.from(audience)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audience mismatch");
        }
        if (currentAccount.store() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store setup required before managing products");
        }
        return new StoreScope(
                currentAccount.store().storeId(),
                currentAccount.store().orgId(),
                currentAccount.user().publicUserId());
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    private record StoreScope(String storeId, String orgId, String ownerPublicUserId) {}
}
