package com.ecommerce.bundle;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.ecommerce.billing.EntitlementService;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.store.StoreProfile;
import com.ecommerce.store.StoreProfileRepository;
import com.ecommerce.warehouse.InventoryLevel;
import com.ecommerce.warehouse.InventoryLevelRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StoreProductBundleService {
    private final ProductBundleRepository bundleRepository;
    private final ProductRepository productRepository;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final StoreProfileRepository storeProfileRepository;
    private final EntitlementService entitlementService;

    public StoreProductBundleService(
            ProductBundleRepository bundleRepository,
            ProductRepository productRepository,
            InventoryLevelRepository inventoryLevelRepository,
            StoreProfileRepository storeProfileRepository,
            EntitlementService entitlementService) {
        this.bundleRepository = bundleRepository;
        this.productRepository = productRepository;
        this.inventoryLevelRepository = inventoryLevelRepository;
        this.storeProfileRepository = storeProfileRepository;
        this.entitlementService = entitlementService;
    }

    public ProductBundleListData list(String storeId, String search, String type, String status, int page, int size) {
        List<ProductBundle> all = bundleRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        String query = normalizeSearch(search);
        Set<BundleType> typeFilters = parseTypeFilters(type);
        Set<BundleStatus> statusFilters = parseStatusFilters(status);

        List<ProductBundle> filtered = all.stream()
                .filter(bundle -> typeFilters.isEmpty() || typeFilters.contains(bundle.getType()))
                .filter(bundle -> statusFilters.isEmpty() || statusFilters.contains(bundle.getStatus()))
                .filter(bundle -> query.isEmpty() || searchable(bundle).contains(query))
                .toList();

        long total = filtered.size();
        List<ProductBundle> pageItems;
        if (size <= 0) {
            pageItems = filtered;
        } else {
            int from = Math.max(0, (Math.max(page, 1) - 1) * size);
            int to = Math.min(filtered.size(), from + size);
            pageItems = from >= filtered.size() ? List.of() : filtered.subList(from, to);
        }

        return new ProductBundleListData(
                pageItems.stream().map(bundle -> toSummary(storeId, bundle)).toList(), total, Math.max(page, 1), size);
    }

    public ProductBundleOverviewData overview(String storeId) {
        List<ProductBundle> all = bundleRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        long active = all.stream().filter(b -> b.getStatus() == BundleStatus.ACTIVE).count();
        long draft = all.stream().filter(b -> b.getStatus() == BundleStatus.DRAFT).count();
        long archived = all.stream().filter(b -> b.getStatus() == BundleStatus.ARCHIVED).count();
        long bundles = all.stream().filter(b -> b.getType() == BundleType.BUNDLE).count();
        long multipacks = all.stream().filter(b -> b.getType() == BundleType.MULTIPACK).count();
        long outOfStock = all.stream().filter(b -> availablePacks(storeId, b) <= 0).count();
        BigDecimal totalSavings = all.stream()
                .map(b -> savings(b))
                .filter(value -> value.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProductBundleOverviewData(
                all.size(), active, draft, archived, bundles, multipacks, outOfStock, money(totalSavings));
    }

    public ProductBundleData get(String storeId, String publicBundleId) {
        return toData(storeId, require(storeId, publicBundleId));
    }

    public ProductBundleData create(String storeId, String ownerPublicUserId, ProductBundleRequest request) {
        entitlementService.require(storeId, EntitlementService.BUNDLES);
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        ProductBundle bundle = new ProductBundle();
        bundle.setStoreId(storeId);
        bundle.setOwnerPublicUserId(ownerPublicUserId);
        applyRequest(bundle, request, storeId);
        ProductBundle saved = bundleRepository.save(bundle);
        if (saved.getBundleCode() == null || saved.getBundleCode().isBlank()) {
            saved.setBundleCode(String.format("BND-%05d", saved.getId()));
            saved = bundleRepository.save(saved);
        }
        return toData(storeId, saved);
    }

    public ProductBundleData update(String storeId, String publicBundleId, ProductBundleRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        ProductBundle bundle = require(storeId, publicBundleId);
        applyRequest(bundle, request, storeId);
        return toData(storeId, bundleRepository.save(bundle));
    }

    public void delete(String storeId, String publicBundleId) {
        bundleRepository.delete(require(storeId, publicBundleId));
    }

    // ----- internals --------------------------------------------------------

    private void applyRequest(ProductBundle bundle, ProductBundleRequest request, String storeId) {
        String name = normalize(request.name());
        if (name == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Bundle name is required");
        }
        BundleType type = BundleType.from(request.type());
        bundle.setType(type);
        bundle.setStatus(BundleStatus.from(request.status()));
        bundle.setName(name);
        bundle.setSku(resolveSku(storeId, bundle, normalize(request.sku())));
        bundle.setImage(normalize(request.image()));
        bundle.setDescription(normalize(request.description()));
        bundle.setPrice(money(request.price()));
        bundle.setCompareAtPrice(request.compareAtPrice() == null ? null : money(request.compareAtPrice()));
        resolveCurrency(bundle, storeId);
        applyItems(bundle, request.items(), type, storeId);
    }

    private void applyItems(
            ProductBundle bundle, List<ProductBundleItemRequest> items, BundleType type, String storeId) {
        bundle.getItems().clear();
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Add at least one product to the bundle");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (ProductBundleItemRequest request : items) {
            if (request == null) {
                continue;
            }
            String productPublicId = normalize(request.productPublicId());
            if (productPublicId == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Each component must reference a product");
            }
            if (!seen.add(productPublicId)) {
                throw new ResponseStatusException(BAD_REQUEST, "The same product was added more than once");
            }
            int quantity = request.quantity() == null ? 0 : request.quantity();
            if (quantity < 1) {
                throw new ResponseStatusException(BAD_REQUEST, "Each component needs a quantity of at least 1");
            }
            Product product = productRepository
                    .findByStoreIdAndPublicProductId(storeId, productPublicId)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Component product not found"));

            ProductBundleItem item = new ProductBundleItem();
            item.setProductPublicId(productPublicId);
            item.setName(product.getTitle());
            item.setSku(product.getSku());
            item.setImage(primaryImage(product));
            item.setQuantity(quantity);
            item.setUnitPrice(money(product.getPrice()));
            item.setUnitCost(money(product.getCostPerItem()));
            bundle.getItems().add(item);
        }
        if (type == BundleType.MULTIPACK && bundle.getItems().size() != 1) {
            throw new ResponseStatusException(BAD_REQUEST, "A multi-pack must contain exactly one product");
        }
    }

    // A blank SKU is allowed (we fall back to the generated bundle code); a
    // provided SKU must be unique within the store.
    private String resolveSku(String storeId, ProductBundle bundle, String requestedSku) {
        if (requestedSku == null) {
            return null;
        }
        bundleRepository
                .findByStoreIdAndSkuIgnoreCase(storeId, requestedSku)
                .filter(existing -> !existing.getId().equals(bundle.getId()))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(CONFLICT, "A bundle with this SKU already exists");
                });
        return requestedSku;
    }

    private void resolveCurrency(ProductBundle bundle, String storeId) {
        StoreProfile profile = storeProfileRepository.findByStoreId(storeId).orElse(null);
        String code = profile != null && profile.getCurrencyCode() != null ? profile.getCurrencyCode() : "INR";
        bundle.setCurrencyCode(code);
        bundle.setCurrencySymbol(resolveSymbol(code));
    }

    private ProductBundle require(String storeId, String publicBundleId) {
        return bundleRepository
                .findByStoreIdAndPublicBundleId(storeId, publicBundleId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Bundle not found"));
    }

    private ProductBundleSummaryData toSummary(String storeId, ProductBundle bundle) {
        return new ProductBundleSummaryData(
                bundle.getPublicBundleId(),
                bundle.getBundleCode(),
                bundle.getType().apiValue(),
                bundle.getStatus().apiValue(),
                bundle.getName(),
                bundle.getSku(),
                bundle.getImage(),
                bundle.getPrice(),
                componentsValue(bundle),
                savings(bundle),
                bundle.getItems().size(),
                totalUnits(bundle),
                availablePacks(storeId, bundle),
                bundle.getCreatedAt(),
                bundle.getUpdatedAt());
    }

    private ProductBundleData toData(String storeId, ProductBundle bundle) {
        BigDecimal value = componentsValue(bundle);
        BigDecimal cost = componentsCost(bundle);
        List<ProductBundleItemData> itemData = bundle.getItems().stream()
                .map(item -> {
                    int available = availableStock(storeId, item.getProductPublicId());
                    int packs = item.getQuantity() == null || item.getQuantity() <= 0
                            ? 0
                            : available / item.getQuantity();
                    return new ProductBundleItemData(
                            item.getProductPublicId(),
                            item.getName(),
                            item.getSku(),
                            item.getImage(),
                            item.getQuantity() == null ? 0 : item.getQuantity(),
                            item.getUnitPrice(),
                            item.getUnitCost(),
                            money(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity() == null ? 0 : item.getQuantity()))),
                            available,
                            packs);
                })
                .toList();
        return new ProductBundleData(
                bundle.getPublicBundleId(),
                bundle.getBundleCode(),
                bundle.getType().apiValue(),
                bundle.getStatus().apiValue(),
                bundle.getName(),
                bundle.getSku(),
                bundle.getImage(),
                bundle.getDescription(),
                bundle.getPrice(),
                bundle.getCompareAtPrice(),
                money(value),
                money(cost),
                money(value.subtract(bundle.getPrice())),
                money(bundle.getPrice().subtract(cost)),
                bundle.getItems().size(),
                totalUnits(bundle),
                availablePacks(storeId, bundle),
                bundle.getCurrencyCode(),
                bundle.getCurrencySymbol(),
                itemData,
                bundle.getCreatedAt(),
                bundle.getUpdatedAt());
    }

    private BigDecimal componentsValue(ProductBundle bundle) {
        return bundle.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity() == null ? 0 : item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal componentsCost(ProductBundle bundle) {
        return bundle.getItems().stream()
                .map(item -> item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity() == null ? 0 : item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal savings(ProductBundle bundle) {
        return money(componentsValue(bundle).subtract(bundle.getPrice()));
    }

    private static int totalUnits(ProductBundle bundle) {
        return bundle.getItems().stream()
                .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
                .sum();
    }

    // Available packs = the most complete bundles we can assemble = the minimum,
    // across components, of floor(componentStock / requiredPerBundle).
    private int availablePacks(String storeId, ProductBundle bundle) {
        if (bundle.getItems().isEmpty()) {
            return 0;
        }
        int packs = Integer.MAX_VALUE;
        for (ProductBundleItem item : bundle.getItems()) {
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            if (qty <= 0) {
                return 0;
            }
            int available = availableStock(storeId, item.getProductPublicId());
            packs = Math.min(packs, available / qty);
        }
        return packs == Integer.MAX_VALUE ? 0 : Math.max(0, packs);
    }

    // Live availability for a component: sum across the product's warehouse
    // inventory levels, falling back to the rolled-up product stock.
    private int availableStock(String storeId, String productPublicId) {
        List<InventoryLevel> levels = inventoryLevelRepository.findByStoreIdAndProductPublicId(storeId, productPublicId);
        if (!levels.isEmpty()) {
            return levels.stream().mapToInt(InventoryLevel::getAvailable).sum();
        }
        return productRepository
                .findByStoreIdAndPublicProductId(storeId, productPublicId)
                .map(product -> product.getStock() == null ? 0 : product.getStock())
                .orElse(0);
    }

    private static String primaryImage(Product product) {
        if (product.getImages() == null || product.getImages().isEmpty()) {
            return null;
        }
        return product.getImages().stream()
                .filter(image -> image.isPrimaryImage())
                .map(image -> image.getUrl())
                .findFirst()
                .orElse(product.getImages().get(0).getUrl());
    }

    private Set<BundleType> parseTypeFilters(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("all")) {
            return Set.of();
        }
        Set<BundleType> types = new LinkedHashSet<>();
        for (String token : value.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("all")) {
                types.add(BundleType.from(trimmed));
            }
        }
        return types;
    }

    private Set<BundleStatus> parseStatusFilters(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("all")) {
            return Set.of();
        }
        Set<BundleStatus> statuses = new LinkedHashSet<>();
        for (String token : value.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("all")) {
                statuses.add(BundleStatus.from(trimmed));
            }
        }
        return statuses;
    }

    private static String searchable(ProductBundle bundle) {
        StringBuilder sb = new StringBuilder();
        sb.append(nullToEmpty(bundle.getName())).append(' ')
                .append(nullToEmpty(bundle.getSku())).append(' ')
                .append(nullToEmpty(bundle.getBundleCode()));
        for (ProductBundleItem item : bundle.getItems()) {
            sb.append(' ').append(nullToEmpty(item.getName())).append(' ').append(nullToEmpty(item.getSku()));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String resolveSymbol(String currencyCode) {
        if (currencyCode == null) {
            return "₹";
        }
        return switch (currencyCode.toUpperCase(Locale.ROOT)) {
            case "USD" -> "$";
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "INR" -> "₹";
            case "NPR" -> "रू";
            case "AUD" -> "A$";
            case "CAD" -> "C$";
            case "SGD" -> "S$";
            case "JPY", "CNY" -> "¥";
            case "AED" -> "د.إ";
            case "PKR", "LKR" -> "Rs";
            case "BDT" -> "৳";
            default -> currencyCode;
        };
    }
}
