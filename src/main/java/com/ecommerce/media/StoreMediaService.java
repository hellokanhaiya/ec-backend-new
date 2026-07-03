package com.ecommerce.media;

import com.ecommerce.product.Product;
import com.ecommerce.product.ProductImage;
import com.ecommerce.product.ProductRepository;
import java.util.ArrayList;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StoreMediaService {
    private final StoreMediaRepository mediaRepository;
    private final ProductMediaStorageService storageService;
    private final ProductRepository productRepository;

    public StoreMediaService(
            StoreMediaRepository mediaRepository,
            ProductMediaStorageService storageService,
            ProductRepository productRepository) {
        this.mediaRepository = mediaRepository;
        this.storageService = storageService;
        this.productRepository = productRepository;
    }

    public MediaListData list(
            String storeId,
            String search,
            String sort,
            String sortDir,
            String mediaType,
            String format,
            String source,
            String category,
            String productSearch,
            Long minSize,
            Long maxSize,
            String dateFrom,
            String dateTo,
            int page,
            int size) {

        List<StoreMedia> all = mediaRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        Map<String, List<MediaProductData>> productsByUrl = productsByMediaUrl(storeId, all);

        List<StoreMedia> filtered = all.stream()
                .filter(m -> matchesSearch(m, search))
                .filter(m -> matchesMediaType(m, mediaType))
                .filter(m -> matchesFormat(m, format))
                .filter(m -> matchesSource(m, source))
                .filter(m -> matchesCategory(productsByUrl.get(m.getUrl()), category))
                .filter(m -> matchesProductSearch(productsByUrl.get(m.getUrl()), productSearch))
                .filter(m -> matchesSizeRange(m, minSize, maxSize))
                .filter(m -> matchesDateRange(m, dateFrom, dateTo))
                .toList();

        List<StoreMedia> sorted = applySort(filtered, sort, sortDir);

        long total = sorted.size();
        int effectiveSize = size <= 0 ? (int) total : size;
        int effectivePage = Math.max(1, page);
        int offset = (effectivePage - 1) * effectiveSize;

        List<MediaData> items = sorted.stream()
                .skip(offset)
                .limit(effectiveSize > 0 ? effectiveSize : total)
                .map(m -> toData(m, productsByUrl.getOrDefault(m.getUrl(), List.of())))
                .toList();

        return new MediaListData(items, total, effectivePage, effectiveSize);
    }

    public MediaData get(String storeId, String publicMediaId) {
        StoreMedia media = mediaRepository
                .findByStoreIdAndPublicMediaId(storeId, publicMediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
        return toData(media, productsByMediaUrl(storeId, List.of(media)).getOrDefault(media.getUrl(), List.of()));
    }

    public MediaData persist(String storeId, ProductMediaUploadData uploadData, String source) {
        StoreMedia media = new StoreMedia();
        media.setStoreId(storeId);
        media.setFileName(uploadData.fileName());
        media.setUrl(uploadData.url());
        media.setContentType(uploadData.contentType());
        media.setSize(uploadData.size());
        media.setObjectName(uploadData.objectName());
        media.setSource(source);
        mediaRepository.save(media);
        return toData(media, List.of());
    }

    public MediaData persistWithLabel(String storeId, ProductMediaUploadData uploadData, String source, String label) {
        StoreMedia media = new StoreMedia();
        media.setStoreId(storeId);
        media.setFileName(uploadData.fileName());
        media.setUrl(uploadData.url());
        media.setContentType(uploadData.contentType());
        media.setSize(uploadData.size());
        media.setObjectName(uploadData.objectName());
        media.setSource(source);
        media.setLabel(label);
        mediaRepository.save(media);
        return toData(media, List.of());
    }

    public void delete(String storeId, String publicMediaId) {
        StoreMedia media = mediaRepository
                .findByStoreIdAndPublicMediaId(storeId, publicMediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
        mediaRepository.delete(media);
        storageService.deleteProductImagesAsync(storeId, Set.of(media.getUrl()));
    }

    private MediaData toData(StoreMedia m, List<MediaProductData> products) {
        return new MediaData(
                m.getPublicMediaId(),
                m.getFileName(),
                m.getUrl(),
                m.getContentType(),
                m.getSize(),
                m.getObjectName(),
                m.getLabel(),
                m.getSource(),
                products,
                m.getCreatedAt(),
                m.getUpdatedAt());
    }

    private Map<String, List<MediaProductData>> productsByMediaUrl(String storeId, List<StoreMedia> media) {
        Set<String> mediaUrls = new LinkedHashSet<>();
        for (StoreMedia item : media) {
            if (item.getUrl() != null && !item.getUrl().isBlank()) {
                mediaUrls.add(item.getUrl());
            }
        }
        if (mediaUrls.isEmpty()) {
            return Map.of();
        }

        Map<String, List<MediaProductData>> productsByUrl = new LinkedHashMap<>();
        List<Product> products = productRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        for (Product product : products) {
            String primaryImage = product.getImages().stream()
                    .filter(ProductImage::isPrimaryImage)
                    .findFirst()
                    .or(() -> product.getImages().stream().findFirst())
                    .map(ProductImage::getUrl)
                    .orElse(null);
            MediaProductData productData = new MediaProductData(
                    product.getPublicProductId(),
                    product.getTitle(),
                    product.getSku(),
                    product.getCategory(),
                    primaryImage);

            for (ProductImage image : product.getImages()) {
                String url = image.getUrl();
                if (url != null && mediaUrls.contains(url)) {
                    productsByUrl.computeIfAbsent(url, ignored -> new ArrayList<>()).add(productData);
                }
            }
        }
        return productsByUrl;
    }

    private boolean matchesSearch(StoreMedia m, String search) {
        if (search == null || search.isBlank()) return true;
        String q = search.trim().toLowerCase(Locale.ROOT);
        return m.getFileName().toLowerCase(Locale.ROOT).contains(q)
                || m.getUrl().toLowerCase(Locale.ROOT).contains(q)
                || m.getContentType().toLowerCase(Locale.ROOT).contains(q)
                || m.getSource().toLowerCase(Locale.ROOT).contains(q)
                || (m.getLabel() != null && m.getLabel().toLowerCase(Locale.ROOT).contains(q));
    }

    private boolean matchesMediaType(StoreMedia m, String mediaType) {
        if (mediaType == null || mediaType.isBlank()) return true;
        String type = mediaType.trim().toLowerCase(Locale.ROOT);
        String contentType = m.getContentType().toLowerCase(Locale.ROOT);
        if (type.equals("gif")) {
            return contentType.equals("image/gif");
        }
        return contentType.startsWith(type + "/");
    }

    private boolean matchesFormat(StoreMedia m, String format) {
        if (format == null || format.isBlank()) return true;
        String fmt = format.trim().toLowerCase(Locale.ROOT);
        String fileName = m.getFileName().toLowerCase(Locale.ROOT);
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0) return false;
        String ext = fileName.substring(lastDot + 1);
        return ext.equals(fmt) || ext.equals("jpg") && fmt.equals("jpeg") || ext.equals("jpeg") && fmt.equals("jpg");
    }

    private boolean matchesSource(StoreMedia m, String source) {
        if (source == null || source.isBlank() || source.equalsIgnoreCase("all")) return true;
        return m.getSource().equalsIgnoreCase(source.trim());
    }

    private boolean matchesCategory(List<MediaProductData> products, String category) {
        if (category == null || category.isBlank() || category.equalsIgnoreCase("all")) return true;
        String q = category.trim().toLowerCase(Locale.ROOT);
        return products != null && products.stream()
                .anyMatch(product -> product.category() != null && product.category().toLowerCase(Locale.ROOT).contains(q));
    }

    private boolean matchesProductSearch(List<MediaProductData> products, String productSearch) {
        if (productSearch == null || productSearch.isBlank()) return true;
        if (products == null || products.isEmpty()) return false;
        String q = productSearch.trim().toLowerCase(Locale.ROOT);
        return products.stream().anyMatch(product ->
                contains(product.title(), q)
                        || contains(product.sku(), q)
                        || contains(product.category(), q)
                        || contains(product.publicProductId(), q));
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesSizeRange(StoreMedia m, Long minSize, Long maxSize) {
        if (minSize != null && m.getSize() < minSize) return false;
        if (maxSize != null && m.getSize() > maxSize) return false;
        return true;
    }

    private boolean matchesDateRange(StoreMedia m, String dateFrom, String dateTo) {
        if (dateFrom != null && !dateFrom.isBlank()) {
            Instant from = LocalDate.parse(dateFrom).atStartOfDay(ZoneOffset.UTC).toInstant();
            if (m.getCreatedAt().isBefore(from)) return false;
        }
        if (dateTo != null && !dateTo.isBlank()) {
            Instant to = LocalDate.parse(dateTo).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            if (!m.getCreatedAt().isBefore(to)) return false;
        }
        return true;
    }

    private List<StoreMedia> applySort(List<StoreMedia> items, String sort, String sortDir) {
        boolean asc = "asc".equalsIgnoreCase(sortDir);
        Comparator<StoreMedia> comparator = switch (sort == null ? "date" : sort.toLowerCase(Locale.ROOT)) {
            case "size" -> Comparator.comparingLong(StoreMedia::getSize);
            case "name" -> Comparator.comparing(m -> m.getFileName().toLowerCase(Locale.ROOT));
            default -> Comparator.comparing(StoreMedia::getCreatedAt);
        };
        if (!asc) {
            comparator = comparator.reversed();
        }
        return items.stream().sorted(comparator).toList();
    }
}
