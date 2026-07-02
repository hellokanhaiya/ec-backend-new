package com.ecommerce.category;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.ecommerce.tag.StoreTagService;
import com.ecommerce.tag.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Category CRUD scoped to a store. Categories form a tree via {@code parentPublicId};
 * the list endpoint returns a flat, store-scoped set the frontend assembles into a
 * tree. Slugs are unique per store — changing one on update records a
 * {@link CategoryRedirect}. Deleting a category promotes its children to its own
 * parent so the tree never orphans.
 */
@Service
@Transactional
public class StoreCategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryRedirectRepository redirectRepository;
    private final StoreTagService storeTagService;

    public StoreCategoryService(
            CategoryRepository categoryRepository,
            CategoryRedirectRepository redirectRepository,
            StoreTagService storeTagService) {
        this.categoryRepository = categoryRepository;
        this.redirectRepository = redirectRepository;
        this.storeTagService = storeTagService;
    }

    public CategoryListData list(String storeId, String search) {
        List<Category> all = categoryRepository.findByStoreIdOrderBySortPositionAscCreatedAtDesc(storeId);
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<CategorySummaryData> items = all.stream()
                .filter(category -> query.isEmpty() || searchable(category).contains(query))
                .map(this::toSummary)
                .toList();
        return new CategoryListData(items, items.size());
    }

    public CategoryData get(String storeId, String publicCategoryId) {
        return toData(require(storeId, publicCategoryId));
    }

    public CategoryData create(String storeId, String ownerPublicUserId, CategoryRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        Category category = new Category();
        category.setStoreId(storeId);
        category.setOwnerPublicUserId(ownerPublicUserId);
        // Append new categories to the end of the manual order.
        category.setSortPosition(categoryRepository.findByStoreIdOrderByCreatedAtDesc(storeId).size());
        applyRequest(category, request, storeId, null);

        Category saved = categoryRepository.save(category);
        if (saved.getCategoryCode() == null || saved.getCategoryCode().isBlank()) {
            saved.setCategoryCode(String.format("CAT-%05d", saved.getId()));
            saved = categoryRepository.save(saved);
        }
        return toData(saved);
    }

    public CategoryData update(String storeId, String publicCategoryId, CategoryRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        Category category = require(storeId, publicCategoryId);
        String oldSlug = category.getSlug();
        applyRequest(category, request, storeId, publicCategoryId);
        // Record a redirect when the slug actually changed.
        if (oldSlug != null && !oldSlug.isBlank() && !oldSlug.equals(category.getSlug())) {
            CategoryRedirect redirect = new CategoryRedirect();
            redirect.setStoreId(storeId);
            redirect.setPublicCategoryId(publicCategoryId);
            redirect.setFromSlug(oldSlug);
            redirect.setToSlug(category.getSlug());
            redirectRepository.save(redirect);
        }
        return toData(categoryRepository.save(category));
    }

    public void delete(String storeId, String publicCategoryId) {
        Category category = require(storeId, publicCategoryId);
        promoteChildren(storeId, category);
        categoryRepository.delete(category);
    }

    public int bulkDelete(String storeId, List<String> publicCategoryIds) {
        if (publicCategoryIds == null || publicCategoryIds.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (String id : publicCategoryIds) {
            var found = categoryRepository.findByStoreIdAndPublicCategoryId(storeId, id);
            if (found.isPresent()) {
                promoteChildren(storeId, found.get());
                categoryRepository.delete(found.get());
                deleted++;
            }
        }
        return deleted;
    }

    /** Persist a new manual order: each id's index becomes its sortPosition. */
    public void reorder(String storeId, List<String> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return;
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            String id = orderedIds.get(i);
            if (id == null || id.isBlank()) {
                continue;
            }
            int position = i;
            categoryRepository
                    .findByStoreIdAndPublicCategoryId(storeId, id)
                    .ifPresent(category -> {
                        category.setSortPosition(position);
                        categoryRepository.save(category);
                    });
        }
    }

    public List<CategoryRedirectData> redirects(String storeId) {
        return redirectRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .map(r -> new CategoryRedirectData(r.getFromSlug(), r.getToSlug(), r.getCreatedAt()))
                .toList();
    }

    // --- helpers -----------------------------------------------------------

    private Category require(String storeId, String publicCategoryId) {
        return categoryRepository.findByStoreIdAndPublicCategoryId(storeId, publicCategoryId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Category not found"));
    }

    /** Re-attach a deleted category's children to its own parent (or root). */
    private void promoteChildren(String storeId, Category category) {
        List<Category> children =
                categoryRepository.findByStoreIdAndParentPublicId(storeId, category.getPublicCategoryId());
        for (Category child : children) {
            child.setParentPublicId(category.getParentPublicId());
            categoryRepository.save(child);
        }
    }

    private void applyRequest(Category category, CategoryRequest request, String storeId, String selfPublicId) {
        String name = request.name() == null ? null : request.name().trim();
        if (name == null || name.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Category name is required");
        }
        category.setName(name);

        String desiredSlug = request.slug() != null && !request.slug().isBlank()
                ? slugify(request.slug())
                : slugify(name);
        category.setSlug(uniqueSlug(storeId, desiredSlug, selfPublicId));

        category.setDescription(normalize(request.description()));
        category.setImage(normalize(request.image()));
        category.setParentPublicId(resolveParent(storeId, request.parentPublicId(), selfPublicId));
        category.setSeoTitle(normalize(request.seoTitle()));
        category.setSeoDescription(normalize(request.seoDescription()));
        category.setSeoKeyword(normalize(request.seoKeyword()));

        // Tags: upsert into the store's shared tag library.
        category.getTags().clear();
        if (request.tags() != null) {
            for (String tagName : request.tags()) {
                if (tagName == null || tagName.isBlank()) {
                    continue;
                }
                category.getTags().add(storeTagService.findOrCreate(storeId, tagName));
            }
        }

        // Product snapshots (only the display values we keep on the category).
        category.getProducts().clear();
        if (request.products() != null) {
            int index = 0;
            for (CategoryProductRequest p : request.products()) {
                if (p == null) {
                    continue;
                }
                CategoryProduct snapshot = new CategoryProduct();
                snapshot.setPublicProductId(normalize(p.publicProductId()));
                snapshot.setName(normalize(p.name()));
                snapshot.setSku(normalize(p.sku()));
                snapshot.setImage(normalize(p.image()));
                snapshot.setPrice(p.price());
                snapshot.setMrp(p.mrp());
                snapshot.setPosition(p.position() != null ? p.position() : index);
                category.getProducts().add(snapshot);
                index++;
            }
        }
    }

    /** Ignore a parent that doesn't exist or points at the category itself. */
    private String resolveParent(String storeId, String parentPublicId, String selfPublicId) {
        String parent = normalize(parentPublicId);
        if (parent == null) {
            return null;
        }
        if (parent.equals(selfPublicId)) {
            throw new ResponseStatusException(BAD_REQUEST, "A category cannot be its own parent");
        }
        return categoryRepository.findByStoreIdAndPublicCategoryId(storeId, parent).isPresent() ? parent : null;
    }

    /** Ensure the slug is unique within the store, suffixing -2, -3, … on collision. */
    private String uniqueSlug(String storeId, String base, String selfPublicId) {
        String root = (base == null || base.isBlank()) ? "category" : base;
        String candidate = root;
        int suffix = 2;
        while (true) {
            var existing = categoryRepository.findByStoreIdAndSlug(storeId, candidate);
            boolean taken = existing.isPresent()
                    && (selfPublicId == null || !existing.get().getPublicCategoryId().equals(selfPublicId));
            if (!taken) {
                return candidate;
            }
            candidate = root + "-" + suffix++;
        }
    }

    private String searchable(Category category) {
        return String.join(
                        " ",
                        nullToEmpty(category.getName()),
                        nullToEmpty(category.getSlug()),
                        nullToEmpty(category.getCategoryCode()))
                .toLowerCase(Locale.ROOT);
    }

    private CategoryData toData(Category category) {
        return new CategoryData(
                category.getPublicCategoryId(),
                category.getCategoryCode(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getImage(),
                category.getParentPublicId(),
                category.getSeoTitle(),
                category.getSeoDescription(),
                category.getSeoKeyword(),
                category.getTags().stream().map(Tag::getName).toList(),
                category.getProducts().stream().map(this::toProductData).toList(),
                category.getProducts().size(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }

    private CategoryProductData toProductData(CategoryProduct p) {
        return new CategoryProductData(
                p.getPublicProductId(), p.getName(), p.getSku(), p.getImage(), p.getPrice(), p.getMrp(), p.getPosition());
    }

    private CategorySummaryData toSummary(Category category) {
        return new CategorySummaryData(
                category.getPublicCategoryId(),
                category.getCategoryCode(),
                category.getName(),
                category.getSlug(),
                category.getImage(),
                category.getParentPublicId(),
                category.getProducts().size(),
                category.getCreatedAt());
    }

    private static String slugify(String value) {
        if (value == null) {
            return "";
        }
        String s = value.toLowerCase(Locale.ROOT).trim();
        s = s.replaceAll("[^a-z0-9]+", "-");
        s = s.replaceAll("^-+|-+$", "");
        return s;
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
