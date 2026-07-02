package com.ecommerce.controller;

import com.ecommerce.auth.AuthAudience;
import com.ecommerce.auth.CurrentAccountData;
import com.ecommerce.auth.CurrentAccountService;
import com.ecommerce.category.CategoryBulkDeleteRequest;
import com.ecommerce.category.CategoryData;
import com.ecommerce.category.CategoryListData;
import com.ecommerce.category.CategoryReorderRequest;
import com.ecommerce.category.CategoryRequest;
import com.ecommerce.category.StoreCategoryService;
import java.util.LinkedHashMap;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class CategoryController {
    private final StoreCategoryService categoryService;
    private final CurrentAccountService currentAccountService;

    public CategoryController(StoreCategoryService categoryService, CurrentAccountService currentAccountService) {
        this.categoryService = categoryService;
        this.currentAccountService = currentAccountService;
    }

    @GetMapping("/{audience}/auth/categories")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search) {
        StoreScope scope = resolveScope(authorization, audience);
        CategoryListData data = categoryService.list(scope.storeId(), search);
        return ok("Categories loaded", data);
    }

    @GetMapping("/{audience}/auth/categories/redirects")
    public ResponseEntity<Map<String, Object>> redirects(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience);
        return ok("Redirects loaded", categoryService.redirects(scope.storeId()));
    }

    @GetMapping("/{audience}/auth/categories/{publicCategoryId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String audience,
            @PathVariable String publicCategoryId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience);
        CategoryData data = categoryService.get(scope.storeId(), publicCategoryId);
        return ok("Category loaded", data);
    }

    @PostMapping("/{audience}/auth/categories")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CategoryRequest request) {
        StoreScope scope = resolveScope(authorization, audience);
        CategoryData data = categoryService.create(scope.storeId(), scope.ownerPublicUserId(), request);
        return ok("Category created", data);
    }

    @PutMapping("/{audience}/auth/categories/{publicCategoryId}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String audience,
            @PathVariable String publicCategoryId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CategoryRequest request) {
        StoreScope scope = resolveScope(authorization, audience);
        CategoryData data = categoryService.update(scope.storeId(), publicCategoryId, request);
        return ok("Category updated", data);
    }

    @DeleteMapping("/{audience}/auth/categories/{publicCategoryId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String audience,
            @PathVariable String publicCategoryId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience);
        categoryService.delete(scope.storeId(), publicCategoryId);
        return ok("Category deleted", null);
    }

    @RequestMapping(value = "/{audience}/auth/categories/reorder", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<Map<String, Object>> reorder(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CategoryReorderRequest request) {
        StoreScope scope = resolveScope(authorization, audience);
        categoryService.reorder(scope.storeId(), request == null ? null : request.ids());
        return ok("Categories reordered", null);
    }

    @PostMapping("/{audience}/auth/categories/bulk-delete")
    public ResponseEntity<Map<String, Object>> bulkDelete(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CategoryBulkDeleteRequest request) {
        StoreScope scope = resolveScope(authorization, audience);
        int deleted = categoryService.bulkDelete(scope.storeId(), request == null ? null : request.ids());
        return ok("Categories deleted", Map.of("deleted", deleted));
    }

    private StoreScope resolveScope(String authorization, String audience) {
        CurrentAccountData currentAccount = currentAccountService.resolveCurrentAccount(authorization);
        if (currentAccount.audience() != AuthAudience.from(audience)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audience mismatch");
        }
        if (currentAccount.store() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store setup required before managing categories");
        }
        return new StoreScope(currentAccount.store().storeId(), currentAccount.user().publicUserId());
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
