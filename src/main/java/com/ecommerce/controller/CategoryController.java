package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.category.CategoryActiveRequest;
import com.ecommerce.category.CategoryBulkDeleteRequest;
import com.ecommerce.category.CategoryData;
import com.ecommerce.category.CategoryListData;
import com.ecommerce.category.CategoryReorderRequest;
import com.ecommerce.category.CategoryRequest;
import com.ecommerce.category.StoreCategoryService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CategoryController {
    private final StoreCategoryService categoryService;
    private final AccessControlService accessControl;

    public CategoryController(StoreCategoryService categoryService, AccessControlService accessControl) {
        this.categoryService = categoryService;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/categories")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_CATEGORIES, AccessLevel.VIEW);
        CategoryListData data = categoryService.list(scope.storeId(), search);
        return ok("Categories loaded", data);
    }

    @GetMapping("/{audience}/auth/categories/redirects")
    public ResponseEntity<Map<String, Object>> redirects(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_CATEGORIES, AccessLevel.VIEW);
        return ok("Redirects loaded", categoryService.redirects(scope.storeId()));
    }

    @GetMapping("/{audience}/auth/categories/{publicCategoryId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String audience,
            @PathVariable String publicCategoryId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_CATEGORIES, AccessLevel.VIEW);
        CategoryData data = categoryService.get(scope.storeId(), publicCategoryId);
        return ok("Category loaded", data);
    }

    @PostMapping("/{audience}/auth/categories")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CategoryRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_CATEGORIES, AccessLevel.MANAGE);
        CategoryData data = categoryService.create(scope.storeId(), scope.ownerPublicUserId(), request);
        return ok("Category created", data);
    }

    @PutMapping("/{audience}/auth/categories/{publicCategoryId}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String audience,
            @PathVariable String publicCategoryId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CategoryRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_CATEGORIES, AccessLevel.EDIT);
        CategoryData data = categoryService.update(scope.storeId(), publicCategoryId, request);
        return ok("Category updated", data);
    }

    @PutMapping("/{audience}/auth/categories/{publicCategoryId}/active")
    public ResponseEntity<Map<String, Object>> setActive(
            @PathVariable String audience,
            @PathVariable String publicCategoryId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CategoryActiveRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_CATEGORIES, AccessLevel.EDIT);
        boolean active = request != null && Boolean.TRUE.equals(request.active());
        CategoryData data = categoryService.setActive(scope.storeId(), publicCategoryId, active);
        return ok("Category updated", data);
    }

    @DeleteMapping("/{audience}/auth/categories/{publicCategoryId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String audience,
            @PathVariable String publicCategoryId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_CATEGORIES, AccessLevel.MANAGE);
        categoryService.delete(scope.storeId(), publicCategoryId);
        return ok("Category deleted", null);
    }

    @RequestMapping(value = "/{audience}/auth/categories/reorder", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<Map<String, Object>> reorder(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CategoryReorderRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_CATEGORIES, AccessLevel.MANAGE);
        categoryService.reorder(scope.storeId(), request == null ? null : request.ids());
        return ok("Categories reordered", null);
    }

    @PostMapping("/{audience}/auth/categories/bulk-delete")
    public ResponseEntity<Map<String, Object>> bulkDelete(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CategoryBulkDeleteRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_CATEGORIES, AccessLevel.MANAGE);
        int deleted = categoryService.bulkDelete(scope.storeId(), request == null ? null : request.ids());
        return ok("Categories deleted", Map.of("deleted", deleted));
    }

    private StoreScope resolveScope(String authorization, String audience, String permissionKey, AccessLevel required) {
        StoreAccessScope scope = accessControl.requireScope(authorization, audience, permissionKey, required);
        return new StoreScope(scope.storeId(), scope.publicUserId());
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    private record StoreScope(String storeId, String ownerPublicUserId) {}
}
