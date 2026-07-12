package com.ecommerce.product;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.ecommerce.media.ProductMediaStorageService;
import com.ecommerce.media.StoreMedia;
import com.ecommerce.media.StoreMediaRepository;
import com.ecommerce.tag.StoreTagService;
import com.ecommerce.tag.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Catalog CRUD scoped to a store, mirroring {@code StoreCustomerService}: fetch the
 * store's products once and filter/paginate in-memory (search + status + category +
 * created-at range), keep an overview aggregate, and manage nested images + the
 * shared tag library on write.
 */
@Service
@Transactional
public class StoreProductService {
    private static final Logger log = LoggerFactory.getLogger(StoreProductService.class);
    /** Stock at or below this is surfaced as "low stock" in the overview. */
    private static final int LOW_STOCK_THRESHOLD = 10;

    private final ProductRepository productRepository;
    private final ProductRedirectRepository redirectRepository;
    private final StoreTagService storeTagService;
    private final ProductMediaStorageService productMediaStorageService;
    private final StoreMediaRepository storeMediaRepository;

    public StoreProductService(
            ProductRepository productRepository,
            ProductRedirectRepository redirectRepository,
            StoreTagService storeTagService,
            ProductMediaStorageService productMediaStorageService,
            StoreMediaRepository storeMediaRepository) {
        this.productRepository = productRepository;
        this.redirectRepository = redirectRepository;
        this.storeTagService = storeTagService;
        this.productMediaStorageService = productMediaStorageService;
        this.storeMediaRepository = storeMediaRepository;
    }

    public ProductListData list(
            String storeId,
            String search,
            String status,
            String category,
            String vendor,
            String minPrice,
            String maxPrice,
            String stockLevel,
            String sort,
            String dateFrom,
            String dateTo,
            int page,
            int size) {
        List<Product> all = productRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        ProductStatus statusFilter =
                (status == null || status.isBlank() || status.equalsIgnoreCase("all")) ? null : ProductStatus.from(status);
        String categoryFilter =
                (category == null || category.isBlank() || category.equalsIgnoreCase("all"))
                        ? null
                        : category.trim().toLowerCase(Locale.ROOT);
        String vendorFilter =
                (vendor == null || vendor.isBlank() || vendor.equalsIgnoreCase("all"))
                        ? null
                        : vendor.trim().toLowerCase(Locale.ROOT);
        BigDecimal min = parseDecimal(minPrice, "minPrice");
        BigDecimal max = parseDecimal(maxPrice, "maxPrice");
        StockLevel stock = parseStockLevel(stockLevel);
        Instant createdFrom = parseInstant(dateFrom);
        Instant createdTo = parseInstant(dateTo);

        List<Product> filtered = all.stream()
                .filter(product -> statusFilter == null || product.getStatus() == statusFilter)
                .filter(product -> categoryFilter == null
                        || (product.getCategory() != null
                                && product.getCategory().toLowerCase(Locale.ROOT).equals(categoryFilter)))
                .filter(product -> vendorFilter == null
                        || (product.getVendor() != null
                                && product.getVendor().toLowerCase(Locale.ROOT).equals(vendorFilter)))
                .filter(product -> withinPrice(product.getPrice(), min, max))
                .filter(product -> matchesStockLevel(product.getStock(), stock))
                .filter(product -> query.isEmpty() || searchable(product).contains(query))
                .filter(product -> withinRange(product.getCreatedAt(), createdFrom, createdTo))
                .sorted(productComparator(sort))
                .toList();

        long total = filtered.size();
        List<Product> pageItems;
        if (size <= 0) {
            pageItems = filtered;
        } else {
            int from = Math.max(0, (Math.max(page, 1) - 1) * size);
            int to = Math.min(filtered.size(), from + size);
            pageItems = from >= filtered.size() ? List.of() : filtered.subList(from, to);
        }

        List<ProductSummaryData> items = pageItems.stream().map(this::toSummary).toList();
        return new ProductListData(items, total, Math.max(page, 1), size);
    }

    /**
     * Resolve the full {@link ProductData} for a bulk export. Precedence: {@code all}
     * exports the whole catalog (narrowed by the same search/status/category filters as
     * the list view); otherwise explicit {@code publicProductIds} are used, falling back
     * to {@code skus}. Unknown ids/skus are skipped.
     */
    @Transactional(readOnly = true)
    public List<ProductData> exportProducts(
            String storeId,
            List<String> publicProductIds,
            List<String> skus,
            boolean all,
            String search,
            String status,
            String category) {
        List<Product> products;
        if (all) {
            String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
            ProductStatus statusFilter =
                    (status == null || status.isBlank() || status.equalsIgnoreCase("all"))
                            ? null
                            : ProductStatus.from(status);
            String categoryFilter =
                    (category == null || category.isBlank() || category.equalsIgnoreCase("all"))
                            ? null
                            : category.trim().toLowerCase(Locale.ROOT);
            products = productRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                    .filter(product -> statusFilter == null || product.getStatus() == statusFilter)
                    .filter(product -> categoryFilter == null
                            || (product.getCategory() != null
                                    && product.getCategory().toLowerCase(Locale.ROOT).equals(categoryFilter)))
                    .filter(product -> query.isEmpty() || searchable(product).contains(query))
                    .toList();
        } else if (publicProductIds != null && !publicProductIds.isEmpty()) {
            products = publicProductIds.stream()
                    .map(id -> productRepository.findByStoreIdAndPublicProductId(storeId, id).orElse(null))
                    .filter(product -> product != null)
                    .toList();
        } else if (skus != null && !skus.isEmpty()) {
            products = skus.stream()
                    .map(sku -> productRepository.findByStoreIdAndSkuIgnoreCase(storeId, sku).orElse(null))
                    .filter(product -> product != null)
                    .toList();
        } else {
            products = List.of();
        }
        return products.stream().map(this::toData).toList();
    }

    public ProductOverviewData overview(String storeId) {
        List<Product> all = productRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        long total = all.size();
        long active = all.stream().filter(p -> p.getStatus() == ProductStatus.ACTIVE).count();
        long draft = all.stream().filter(p -> p.getStatus() == ProductStatus.DRAFT).count();
        long archived = all.stream().filter(p -> p.getStatus() == ProductStatus.ARCHIVED).count();
        long lowStock = all.stream()
                .filter(p -> p.getStock() != null && p.getStock() <= LOW_STOCK_THRESHOLD)
                .count();
        Set<String> categories = new TreeSet<>();
        // Distinct vendors, de-duplicated case-insensitively but preserving the first
        // spelling seen, sorted for a stable filter dropdown.
        java.util.Map<String, String> vendorsByKey = new java.util.TreeMap<>();
        for (Product p : all) {
            if (p.getCategory() != null && !p.getCategory().isBlank()) {
                categories.add(p.getCategory().trim().toLowerCase(Locale.ROOT));
            }
            if (p.getVendor() != null && !p.getVendor().isBlank()) {
                String vendorName = p.getVendor().trim();
                vendorsByKey.putIfAbsent(vendorName.toLowerCase(Locale.ROOT), vendorName);
            }
        }
        BigDecimal avgPrice = BigDecimal.ZERO;
        if (!all.isEmpty()) {
            BigDecimal sum = all.stream()
                    .map(p -> p.getPrice() == null ? BigDecimal.ZERO : p.getPrice())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgPrice = sum.divide(BigDecimal.valueOf(all.size()), 2, RoundingMode.HALF_UP);
        }
        return new ProductOverviewData(
                total, active, draft, archived, lowStock, categories.size(), avgPrice,
                List.copyOf(vendorsByKey.values()));
    }

    public ProductPickerListData picker(String storeId, String search, int page, int size) {
        List<Product> all = productRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Product> filtered = all.stream()
                .filter(product -> product.getStatus() == ProductStatus.ACTIVE)
                .filter(product -> query.isEmpty() || searchable(product).contains(query))
                .toList();

        long total = filtered.size();
        List<Product> pageItems;
        boolean hasMore;
        if (size <= 0) {
            pageItems = filtered;
            hasMore = false;
        } else {
            int from = Math.max(0, (Math.max(page, 1) - 1) * size);
            int to = Math.min(filtered.size(), from + size);
            pageItems = from >= filtered.size() ? List.of() : filtered.subList(from, to);
            hasMore = to < filtered.size();
        }
        return new ProductPickerListData(pageItems.stream().map(this::toPicker).toList(), total, Math.max(page, 1), size, hasMore);
    }

    /**
     * System "related products" suggestions: active products in the same category,
     * excluding the product itself and anything the client already linked or dismissed.
     * This is the cold-start (category-based) recommendation used until behavioural
     * data (co-purchase) is available. Newest first; capped at {@code limit} (default 6).
     */
    public List<ProductSummaryData> relatedSuggestions(String storeId, RelatedSuggestionRequest request) {
        if (request == null) {
            return List.of();
        }
        String category = request.category() == null ? "" : request.category().trim().toLowerCase(Locale.ROOT);
        if (category.isEmpty()) {
            return List.of();
        }

        Set<String> exclude = new LinkedHashSet<>();
        String selfId = request.excludeId() == null ? null : request.excludeId().trim();
        if (selfId != null && !selfId.isEmpty()) {
            exclude.add(selfId);
        }
        if (request.excludeIds() != null) {
            for (String id : request.excludeIds()) {
                if (id != null && !id.isBlank()) {
                    exclude.add(id.trim());
                }
            }
        }
        // Defensive: also honour whatever the saved product already links / dismissed.
        if (selfId != null && !selfId.isEmpty()) {
            productRepository.findByStoreIdAndPublicProductId(storeId, selfId).ifPresent(self -> {
                exclude.addAll(self.getDismissedRelatedProductIds());
                self.getRelatedProducts().forEach(r -> exclude.add(r.getPublicProductId()));
            });
        }

        int limit = request.limit() != null && request.limit() > 0 ? Math.min(request.limit(), 24) : 6;
        return productRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .filter(product -> product.getStatus() == ProductStatus.ACTIVE)
                .filter(product -> product.getCategory() != null
                        && product.getCategory().toLowerCase(Locale.ROOT).equals(category))
                .filter(product -> !exclude.contains(product.getPublicProductId()))
                .limit(limit)
                .map(this::toSummary)
                .toList();
    }

    public ProductData get(String storeId, String publicProductId) {
        return toData(require(storeId, publicProductId));
    }

    public ProductData create(String storeId, String ownerPublicUserId, ProductRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        Product product = new Product();
        product.setStoreId(storeId);
        product.setOwnerPublicUserId(ownerPublicUserId);
        applyRequest(product, request, storeId);

        Product saved = productRepository.save(product);
        boolean resave = false;
        if (saved.getProductCode() == null || saved.getProductCode().isBlank()) {
            saved.setProductCode(String.format("PRD-%05d", saved.getId()));
            resave = true;
        }
        // Auto-generate a SKU when the merchant left it blank (id-based → unique per store).
        if (saved.getSku() == null || saved.getSku().isBlank()) {
            saved.setSku(String.format("SKU-%05d", saved.getId()));
            resave = true;
        }
        if (resave) {
            saved = productRepository.save(saved);
        }
        return toData(saved);
    }

    public ProductData update(String storeId, String orgId, String publicProductId, ProductRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        Product product = require(storeId, publicProductId);
        Set<String> previousImages = imageUrls(product);
        String oldSlug = product.getSlug();
        applyRequest(product, request, storeId);
        Product saved = productRepository.save(product);
        Set<String> currentImages = imageUrls(saved);
        log.info("update: product {} previousImages={}, currentImages={}", publicProductId, previousImages.size(), currentImages.size());
        cleanupRemovedImages(storeId, orgId, publicProductId, previousImages, currentImages);

        // Record a 301 redirect when the slug changed and the merchant opted in.
        if (Boolean.TRUE.equals(request.createRedirect())
                && oldSlug != null
                && !oldSlug.isBlank()
                && !oldSlug.equals(saved.getSlug())) {
            ProductRedirect redirect = new ProductRedirect();
            redirect.setStoreId(storeId);
            redirect.setPublicProductId(publicProductId);
            redirect.setFromSlug(oldSlug);
            redirect.setToSlug(saved.getSlug());
            redirectRepository.save(redirect);
        }
        return toData(saved);
    }

    public void delete(String storeId, String orgId, String publicProductId) {
        Product product = require(storeId, publicProductId);
        Set<String> images = imageUrls(product);
        log.info("delete: product {} has {} images to clean up", publicProductId, images.size());
        deleteImagesAfterCommit(storeId, orgId, publicProductId, images);
        productRepository.delete(product);
    }

    public List<ProductRedirectData> redirects(String storeId) {
        return redirectRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .map(r -> new ProductRedirectData(r.getPublicProductId(), r.getFromSlug(), r.getToSlug(), r.getCreatedAt()))
                .toList();
    }

    // --- helpers -----------------------------------------------------------

    private Product require(String storeId, String publicProductId) {
        return productRepository.findByStoreIdAndPublicProductId(storeId, publicProductId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found"));
    }

    private Set<String> imageUrls(Product product) {
        Set<String> urls = new LinkedHashSet<>();
        if (product == null || product.getImages() == null) {
            return urls;
        }
        for (ProductImage image : product.getImages()) {
            if (image.getUrl() != null && !image.getUrl().isBlank()) {
                urls.add(image.getUrl().trim());
            }
        }
        return urls;
    }

    private void cleanupRemovedImages(
            String storeId,
            String legacyOrgId,
            String publicProductId,
            Set<String> previousImages,
            Set<String> currentImages) {
        if (previousImages == null || previousImages.isEmpty()) {
            log.debug("cleanupRemovedImages: no previous images for product {}", publicProductId);
            return;
        }
        Set<String> removed = new LinkedHashSet<>(previousImages);
        if (currentImages != null) {
            removed.removeAll(currentImages);
        }
        log.info("cleanupRemovedImages: product {} removed {} images: {}", publicProductId, removed.size(), removed);
        deleteImagesAfterCommit(storeId, legacyOrgId, publicProductId, removed);
    }

    private void deleteImagesAfterCommit(String storeId, String legacyOrgId, String publicProductId, Set<String> urls) {
        if (urls == null || urls.isEmpty()) {
            log.debug("deleteImagesAfterCommit: no urls to delete for product {}", publicProductId);
            return;
        }
        Set<String> deleteableUrls = unusedImageUrls(storeId, publicProductId, urls);
        if (deleteableUrls.isEmpty()) {
            log.debug("deleteImagesAfterCommit: all urls still in use for product {}", publicProductId);
            return;
        }
        log.info("deleteImagesAfterCommit: deleting {} images for product {}: {}", deleteableUrls.size(), publicProductId, deleteableUrls);
        removeMediaLibraryRows(storeId, deleteableUrls);
        Set<String> copy = new LinkedHashSet<>(deleteableUrls);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            log.debug("deleteImagesAfterCommit: registering afterCommit callback for product {}", publicProductId);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.debug("deleteImagesAfterCommit: afterCommit fired, calling deleteProductImagesAsync for product {}", publicProductId);
                    productMediaStorageService.deleteProductImagesAsync(storeId, legacyOrgId, copy);
                }
            });
        } else {
            log.warn("deleteImagesAfterCommit: no active synchronization, calling deleteProductImagesAsync immediately for product {}", publicProductId);
            productMediaStorageService.deleteProductImagesAsync(storeId, legacyOrgId, copy);
        }
    }

    private Set<String> unusedImageUrls(String storeId, String publicProductId, Set<String> urls) {
        Set<String> normalizedUrls = normalizeUrls(urls);
        if (normalizedUrls.isEmpty()) {
            return normalizedUrls;
        }
        Set<String> urlsStillInUse =
                productRepository.findImageUrlsStillInUseByOtherProducts(storeId, publicProductId, normalizedUrls);
        normalizedUrls.removeAll(urlsStillInUse);
        return normalizedUrls;
    }

    private void removeMediaLibraryRows(String storeId, Set<String> urls) {
        List<StoreMedia> mediaRows = storeMediaRepository.findByStoreIdAndUrlIn(storeId, urls);
        if (!mediaRows.isEmpty()) {
            storeMediaRepository.deleteAll(mediaRows);
        }
    }

    private Set<String> normalizeUrls(Set<String> urls) {
        Set<String> normalizedUrls = new LinkedHashSet<>();
        if (urls == null) {
            return normalizedUrls;
        }
        for (String url : urls) {
            if (url != null && !url.isBlank()) {
                normalizedUrls.add(url.trim());
            }
        }
        return normalizedUrls;
    }

    private void applyRequest(Product product, ProductRequest request, String storeId) {
        String title = request.title() == null ? null : request.title().trim();
        if (title == null || title.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Product title is required");
        }
        product.setTitle(title);
        product.setSlug(normalize(request.slug()));
        product.setSummary(normalize(request.summary()));
        product.setDescription(normalize(request.description()));
        product.setStatus(ProductStatus.from(request.status()));
        product.setProductType(ProductType.from(request.productType()));
        product.setCategory(normalize(request.category()));
        product.setCategoryPath(normalize(request.categoryPath()));
        product.setCategoryPublicId(normalize(request.categoryPublicId()));
        product.setVendor(normalize(request.vendor()));

        if (request.price() == null || request.price().signum() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Product price is required");
        }
        product.setPrice(request.price());
        product.setCompareAtPrice(request.compareAtPrice());
        product.setCostPerItem(request.costPerItem());

        resolveSku(product, request, storeId);
        resolveBarcode(product, request);
        product.setStock(request.stock() != null ? request.stock() : 0);
        product.setTrackInventory(request.trackInventory() == null || request.trackInventory());

        product.setRequiresShipping(request.requiresShipping() == null || request.requiresShipping());
        product.setWeight(request.weight());
        product.setLength(request.length());
        product.setWidth(request.width());
        product.setHeight(request.height());
        product.setCountryOfOrigin(normalize(request.countryOfOrigin()));

        product.setTaxable(request.taxable() == null || request.taxable());
        product.setHsnCode(normalize(request.hsnCode()));
        product.setTaxCode(normalize(request.taxCode()));
        product.setTaxRate(request.taxRate());

        product.setSeoTitle(normalize(request.seoTitle()));
        product.setSeoDescription(normalize(request.seoDescription()));
        product.setSeoKeyword(normalize(request.seoKeyword()));

        // Tags: upsert into the store's shared tag library and link to this product.
        product.getTags().clear();
        if (request.tags() != null) {
            for (String name : request.tags()) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                product.getTags().add(storeTagService.findOrCreate(storeId, name));
            }
        }

        applyImages(product, request.images());
        applyRelatedProducts(product, request.relatedProducts());

        // Dismissed related-product suggestions (ids the merchant removed so they
        // are never auto-suggested again).
        product.getDismissedRelatedProductIds().clear();
        if (request.dismissedRelatedProductIds() != null) {
            for (String id : request.dismissedRelatedProductIds()) {
                if (id != null && !id.isBlank()) {
                    product.getDismissedRelatedProductIds().add(id.trim());
                }
            }
        }
    }

    /**
     * Related products (cross-sell) are stored as ordered snapshots — clear and
     * rebuild from the request, skipping blank ids, self-references and duplicates.
     */
    private void applyRelatedProducts(Product product, List<RelatedProductRequest> requests) {
        product.getRelatedProducts().clear();
        if (requests == null || requests.isEmpty()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        String ownId = product.getPublicProductId();
        int index = 0;
        for (RelatedProductRequest request : requests) {
            if (request == null || request.publicProductId() == null || request.publicProductId().isBlank()) {
                continue;
            }
            String id = request.publicProductId().trim();
            if (id.equals(ownId) || !seen.add(id)) {
                continue;
            }
            RelatedProduct related = new RelatedProduct();
            related.setPublicProductId(id);
            related.setName(normalize(request.name()));
            related.setSku(normalize(request.sku()));
            related.setImage(normalize(request.image()));
            related.setPrice(request.price());
            related.setMrp(request.mrp());
            related.setPosition(request.position() != null ? request.position() : index);
            product.getRelatedProducts().add(related);
            index++;
        }
    }

    private void applyImages(Product product, List<ProductImageRequest> requests) {
        product.getImages().clear();
        if (requests == null || requests.isEmpty()) {
            return;
        }
        List<ProductImage> built = new ArrayList<>();
        boolean anyPrimary = false;
        int index = 0;
        for (ProductImageRequest request : requests) {
            if (request == null || request.url() == null || request.url().isBlank()) {
                continue;
            }
            ProductImage image = new ProductImage();
            image.setUrl(request.url().trim());
            image.setAltText(normalize(request.altText()));
            image.setPosition(request.position() != null ? request.position() : index);
            boolean isPrimary = Boolean.TRUE.equals(request.isPrimary());
            image.setPrimaryImage(isPrimary);
            anyPrimary = anyPrimary || isPrimary;
            built.add(image);
            index++;
        }
        if (built.isEmpty()) {
            return;
        }
        // Guarantee exactly one primary image (first one if none flagged).
        if (!anyPrimary) {
            built.get(0).setPrimaryImage(true);
        } else {
            boolean seen = false;
            for (ProductImage image : built) {
                if (image.isPrimaryImage()) {
                    if (seen) {
                        image.setPrimaryImage(false);
                    } else {
                        seen = true;
                    }
                }
            }
        }
        product.getImages().addAll(built);
    }

    private String searchable(Product product) {
        return String.join(
                        " ",
                        nullToEmpty(product.getTitle()),
                        nullToEmpty(product.getSku()),
                        nullToEmpty(product.getVendor()),
                        nullToEmpty(product.getCategory()),
                        nullToEmpty(product.getProductCode()))
                .toLowerCase(Locale.ROOT);
    }

    private ProductData toData(Product product) {
        return new ProductData(
                product.getPublicProductId(),
                product.getProductCode(),
                product.getTitle(),
                product.getSlug(),
                product.getSummary(),
                product.getDescription(),
                product.getStatus().name(),
                product.getProductType().name(),
                product.getCategory(),
                product.getCategoryPath(),
                product.getCategoryPublicId(),
                product.getVendor(),
                product.getPrice(),
                product.getCompareAtPrice(),
                product.getCostPerItem(),
                product.getSku(),
                product.getBarcode(),
                product.getStock(),
                product.isTrackInventory(),
                product.isRequiresShipping(),
                product.getWeight(),
                product.getLength(),
                product.getWidth(),
                product.getHeight(),
                product.getCountryOfOrigin(),
                product.isTaxable(),
                product.getHsnCode(),
                product.getTaxCode(),
                product.getTaxRate(),
                product.getSeoTitle(),
                product.getSeoDescription(),
                product.getSeoKeyword(),
                product.getSalesCount() == null ? 0 : product.getSalesCount(),
                product.getTags().stream().map(Tag::getName).toList(),
                product.getImages().stream().map(this::toImageData).toList(),
                product.getRelatedProducts().stream().map(this::toRelatedData).toList(),
                List.copyOf(product.getDismissedRelatedProductIds()),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    private ProductImageData toImageData(ProductImage image) {
        return new ProductImageData(
                image.getId(), image.getUrl(), image.getAltText(), image.getPosition(), image.isPrimaryImage());
    }

    private RelatedProductData toRelatedData(RelatedProduct related) {
        return new RelatedProductData(
                related.getPublicProductId(),
                related.getName(),
                related.getSku(),
                related.getImage(),
                related.getPrice(),
                related.getMrp(),
                related.getPosition());
    }

    private ProductSummaryData toSummary(Product product) {
        String image = product.getImages().stream()
                .filter(ProductImage::isPrimaryImage)
                .findFirst()
                .or(() -> product.getImages().stream().findFirst())
                .map(ProductImage::getUrl)
                .orElse(null);
        return new ProductSummaryData(
                product.getPublicProductId(),
                product.getProductCode(),
                product.getTitle(),
                image,
                product.getSku(),
                product.getStatus().name(),
                product.getCategory(),
                product.getVendor(),
                product.getStock(),
                product.getPrice(),
                product.getCompareAtPrice(),
                product.getSalesCount() == null ? 0 : product.getSalesCount(),
                product.getTags().stream().map(Tag::getName).toList(),
                product.getCreatedAt());
    }

    private ProductPickerData toPicker(Product product) {
        String image = product.getImages().stream()
                .filter(ProductImage::isPrimaryImage)
                .findFirst()
                .or(() -> product.getImages().stream().findFirst())
                .map(ProductImage::getUrl)
                .orElse(null);
        return new ProductPickerData(
                product.getPublicProductId(),
                product.getTitle(),
                product.getSku(),
                image,
                product.getPrice(),
                product.getStock(),
                product.isTaxable(),
                product.getTaxRate(),
                product.getVendor(),
                product.getCategory());
    }

    /** Stock buckets the advanced filter offers, mirroring the overview's low-stock rule. */
    private enum StockLevel {
        IN_STOCK,
        LOW_STOCK,
        OUT_OF_STOCK
    }

    /** Parse a decimal price bound from the advanced filter; blank means "no bound". */
    private static BigDecimal parseDecimal(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid " + field + " filter value: " + value);
        }
    }

    /** Map the incoming stock-level token to a bucket; blank/unknown means "no filter". */
    private static StockLevel parseStockLevel(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("all")) {
            return null;
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "in_stock", "in-stock", "instock" -> {
                return StockLevel.IN_STOCK;
            }
            case "low_stock", "low-stock", "low" -> {
                return StockLevel.LOW_STOCK;
            }
            case "out_of_stock", "out-of-stock", "outofstock", "out" -> {
                return StockLevel.OUT_OF_STOCK;
            }
            default -> {
                return null;
            }
        }
    }

    /** Inclusive [min, max] window on a product's price; null bounds are open-ended, null price treated as 0. */
    private static boolean withinPrice(BigDecimal price, BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return true;
        }
        BigDecimal value = price == null ? BigDecimal.ZERO : price;
        if (min != null && value.compareTo(min) < 0) {
            return false;
        }
        return max == null || value.compareTo(max) <= 0;
    }

    /** Bucket a product's stock the same way the overview does (≤ threshold = low, ≤ 0 = out). */
    private static boolean matchesStockLevel(Integer stock, StockLevel level) {
        if (level == null) {
            return true;
        }
        int qty = stock == null ? 0 : stock;
        return switch (level) {
            case IN_STOCK -> qty > 0;
            case LOW_STOCK -> qty > 0 && qty <= LOW_STOCK_THRESHOLD;
            case OUT_OF_STOCK -> qty <= 0;
        };
    }

    /** Sort the filtered page. Default keeps the repository's newest-first order. */
    private static java.util.Comparator<Product> productComparator(String sort) {
        String key = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        java.util.Comparator<BigDecimal> priceNulls = java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder());
        java.util.Comparator<Integer> stockNulls = java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder());
        java.util.Comparator<Instant> createdNulls = java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder());
        return switch (key) {
            case "oldest", "created_asc" -> java.util.Comparator.comparing(Product::getCreatedAt, createdNulls);
            case "price_asc", "price_low" -> java.util.Comparator.comparing(Product::getPrice, priceNulls);
            case "price_desc", "price_high" -> java.util.Comparator.comparing(Product::getPrice, priceNulls).reversed();
            case "title_asc", "name_asc" -> java.util.Comparator.comparing(
                    p -> p.getTitle() == null ? "" : p.getTitle().toLowerCase(Locale.ROOT));
            case "title_desc", "name_desc" -> java.util.Comparator.comparing(
                    (Product p) -> p.getTitle() == null ? "" : p.getTitle().toLowerCase(Locale.ROOT)).reversed();
            case "stock_asc", "stock_low" -> java.util.Comparator.comparing(Product::getStock, stockNulls);
            case "stock_desc", "stock_high" -> java.util.Comparator.comparing(Product::getStock, stockNulls).reversed();
            // "newest" / "created_desc" / anything else → newest first (repository default order).
            default -> java.util.Comparator.comparing(Product::getCreatedAt, createdNulls).reversed();
        };
    }

    /** Parse an ISO-8601 instant sent by the date-range filter; blank means "no bound". */
    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid date filter value: " + value);
        }
    }

    /** Inclusive [from, to] window on a product's creation time; null bounds are open-ended. */
    private static boolean withinRange(Instant createdAt, Instant from, Instant to) {
        if (from == null && to == null) {
            return true;
        }
        if (createdAt == null) {
            return false;
        }
        if (from != null && createdAt.isBefore(from)) {
            return false;
        }
        return to == null || !createdAt.isAfter(to);
    }

    /** Use a merchant-supplied SKU (rejecting duplicates within the store) or leave it
     * blank so {@link #create} can auto-generate one from the product code. */
    private void resolveSku(Product product, ProductRequest request, String storeId) {
        String requested = normalize(request.sku());
        if (requested == null) {
            return; // keep existing (update) or leave null → auto-generated on create
        }
        productRepository.findByStoreIdAndSkuIgnoreCase(storeId, requested).ifPresent(other -> {
            if (!other.getPublicProductId().equals(product.getPublicProductId())) {
                throw new ResponseStatusException(
                        CONFLICT, "SKU \"" + requested + "\" is already used by another product");
            }
        });
        product.setSku(requested);
    }

    private void resolveBarcode(Product product, ProductRequest request) {
        String requested = normalize(request.barcode());
        if (requested != null) {
            product.setBarcode(requested);
        } else if (product.getBarcode() == null || product.getBarcode().isBlank()) {
            product.setBarcode(generateBarcode());
        }
    }

    private static String generateBarcode() {
        StringBuilder sb = new StringBuilder(13);
        for (int i = 0; i < 13; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
