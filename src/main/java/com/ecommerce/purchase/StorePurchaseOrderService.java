package com.ecommerce.purchase;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.store.StoreProfile;
import com.ecommerce.store.StoreProfileRepository;
import com.ecommerce.warehouse.StoreInventoryService;
import com.ecommerce.warehouse.StoreWarehouseService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StorePurchaseOrderService {
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseSupplierRepository supplierRepository;
    private final PurchaseOrderNumberSequenceRepository sequenceRepository;
    private final ProductRepository productRepository;
    private final StoreProfileRepository storeProfileRepository;
    private final StoreWarehouseService warehouseService;
    private final StoreInventoryService inventoryService;

    public StorePurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseSupplierRepository supplierRepository,
            PurchaseOrderNumberSequenceRepository sequenceRepository,
            ProductRepository productRepository,
            StoreProfileRepository storeProfileRepository,
            StoreWarehouseService warehouseService,
            StoreInventoryService inventoryService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.sequenceRepository = sequenceRepository;
        this.productRepository = productRepository;
        this.storeProfileRepository = storeProfileRepository;
        this.warehouseService = warehouseService;
        this.inventoryService = inventoryService;
    }

    public PurchaseOrderListData list(
            String storeId, String search, String status, String supplierSearch, int page, int size) {
        List<PurchaseOrder> all = purchaseOrderRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        String query = normalizeSearch(search);
        String supplierQuery = normalizeSearch(supplierSearch);
        PurchaseOrderStatus statusFilter = parseStatusFilter(status);

        List<PurchaseOrder> filtered = all.stream()
                .filter(order -> statusFilter == null || order.getStatus() == statusFilter)
                .filter(order -> query.isEmpty() || searchable(order).contains(query))
                .filter(order -> supplierQuery.isEmpty() || supplierMatches(order, supplierQuery))
                .toList();

        long total = filtered.size();
        List<PurchaseOrder> pageItems;
        if (size <= 0) {
            pageItems = filtered;
        } else {
            int from = Math.max(0, (Math.max(page, 1) - 1) * size);
            int to = Math.min(filtered.size(), from + size);
            pageItems = from >= filtered.size() ? List.of() : filtered.subList(from, to);
        }

        return new PurchaseOrderListData(
                pageItems.stream().map(this::toSummary).toList(), total, Math.max(page, 1), size);
    }

    public PurchaseOrderOverviewData overview(String storeId) {
        List<PurchaseOrder> all = purchaseOrderRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        long draft = all.stream().filter(order -> order.getStatus() == PurchaseOrderStatus.DRAFT).count();
        long ordered = all.stream().filter(order -> order.getStatus() == PurchaseOrderStatus.ORDERED).count();
        long received = all.stream().filter(order -> order.getStatus() == PurchaseOrderStatus.RECEIVED).count();
        long cancelled = all.stream().filter(order -> order.getStatus() == PurchaseOrderStatus.CANCELLED).count();
        BigDecimal totalValue = all.stream()
                .map(PurchaseOrder::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PurchaseOrderOverviewData(all.size(), draft, ordered, received, cancelled, ordered, money(totalValue));
    }

    public PurchaseOrderData get(String storeId, String publicPurchaseOrderId) {
        return toData(require(storeId, publicPurchaseOrderId));
    }

    public PurchaseOrderData create(String storeId, String ownerPublicUserId, PurchaseOrderRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        PurchaseOrder order = new PurchaseOrder();
        order.setStoreId(storeId);
        order.setOwnerPublicUserId(ownerPublicUserId);
        applyRequest(order, request, storeId, ownerPublicUserId, true);
        order.setPurchaseOrderNumber(nextPurchaseOrderNumber(storeId));
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        applyInventoryIfReceived(saved);
        return toData(saved);
    }

    public PurchaseOrderData update(String storeId, String publicPurchaseOrderId, PurchaseOrderRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        PurchaseOrder order = require(storeId, publicPurchaseOrderId);
        ensureEditable(order);
        applyRequest(order, request, storeId, order.getOwnerPublicUserId(), false);
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        applyInventoryIfReceived(saved);
        return toData(saved);
    }

    public PurchaseOrderData updateStatus(String storeId, String publicPurchaseOrderId, String status) {
        PurchaseOrder order = require(storeId, publicPurchaseOrderId);
        PurchaseOrderStatus next = parseStatus(status);
        if (order.getStatus() == next) {
            return toData(order);
        }
        if (order.getStatus() == PurchaseOrderStatus.DRAFT
                && next != PurchaseOrderStatus.ORDERED
                && next != PurchaseOrderStatus.CANCELLED) {
            throw new ResponseStatusException(BAD_REQUEST, "Draft purchase orders can only move to ordered or cancelled");
        }
        if (order.getStatus() == PurchaseOrderStatus.ORDERED
                && next != PurchaseOrderStatus.RECEIVED
                && next != PurchaseOrderStatus.CANCELLED) {
            throw new ResponseStatusException(BAD_REQUEST, "Ordered purchase orders can only move to received or cancelled");
        }
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED && next != PurchaseOrderStatus.RECEIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Received purchase orders cannot change status");
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED && next != PurchaseOrderStatus.CANCELLED) {
            throw new ResponseStatusException(BAD_REQUEST, "Cancelled purchase orders cannot be reopened");
        }
        order.setStatus(next);
        if (next == PurchaseOrderStatus.RECEIVED) {
            order.setReceivedAt(Instant.now());
        }
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        applyInventoryIfReceived(saved);
        return toData(saved);
    }

    public void delete(String storeId, String publicPurchaseOrderId) {
        purchaseOrderRepository.delete(require(storeId, publicPurchaseOrderId));
    }

    public byte[] exportCsv(String storeId, List<String> ids) {
        StringBuilder csv = new StringBuilder();
        csv.append(
                "PO Number,Supplier,Email,Phone,Company,Status,Order Date,Expected Date,Received At,Items,Subtotal,Total,Warehouse,Reference,Notes,Created At\n");
        for (PurchaseOrder order : selectedOrders(storeId, ids)) {
            csv.append(csv(order.getPurchaseOrderNumber())).append(',')
                    .append(csv(order.getSupplierName())).append(',')
                    .append(csv(order.getSupplierEmail())).append(',')
                    .append(csv(order.getSupplierPhone())).append(',')
                    .append(csv(order.getSupplierCompany())).append(',')
                    .append(csv(order.getStatus().apiValue())).append(',')
                    .append(csv(order.getOrderDate() == null ? null : order.getOrderDate().toString())).append(',')
                    .append(csv(order.getExpectedDate() == null ? null : order.getExpectedDate().toString())).append(',')
                    .append(csv(order.getReceivedAt() == null ? null : order.getReceivedAt().toString())).append(',')
                    .append(itemCount(order)).append(',')
                    .append(order.getSubtotal()).append(',')
                    .append(order.getTotal()).append(',')
                    .append(csv(order.getWarehousePublicId())).append(',')
                    .append(csv(order.getReferenceNumber())).append(',')
                    .append(csv(order.getNotes())).append(',')
                    .append(csv(order.getCreatedAt() == null ? null : order.getCreatedAt().toString()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public PurchaseSupplierListData listSuppliers(String storeId, String search, int page, int size) {
        List<PurchaseSupplier> all = supplierRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        String query = normalizeSearch(search);
        List<PurchaseSupplier> filtered = all.stream()
                .filter(supplier -> query.isEmpty() || searchable(supplier).contains(query))
                .toList();
        long total = filtered.size();
        List<PurchaseSupplier> pageItems;
        if (size <= 0) {
            pageItems = filtered;
        } else {
            int from = Math.max(0, (Math.max(page, 1) - 1) * size);
            int to = Math.min(filtered.size(), from + size);
            pageItems = from >= filtered.size() ? List.of() : filtered.subList(from, to);
        }
        return new PurchaseSupplierListData(pageItems.stream().map(this::toData).toList(), total, Math.max(page, 1), size);
    }

    public PurchaseSupplierData createSupplier(String storeId, String ownerPublicUserId, PurchaseSupplierRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        PurchaseSupplier supplier = new PurchaseSupplier();
        supplier.setStoreId(storeId);
        supplier.setOwnerPublicUserId(ownerPublicUserId);
        applySupplierRequest(supplier, request);
        PurchaseSupplier saved = supplierRepository.save(supplier);
        if (saved.getSupplierCode() == null || saved.getSupplierCode().isBlank()) {
            saved.setSupplierCode(String.format("SUP-%05d", saved.getId()));
            saved = supplierRepository.save(saved);
        }
        return toData(saved);
    }

    public PurchaseSupplierData getSupplier(String storeId, String publicSupplierId) {
        return toData(requireSupplier(storeId, publicSupplierId));
    }

    public PurchaseSupplierData updateSupplier(
            String storeId, String publicSupplierId, PurchaseSupplierRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        PurchaseSupplier supplier = requireSupplier(storeId, publicSupplierId);
        applySupplierRequest(supplier, request);
        return toData(supplierRepository.save(supplier));
    }

    public List<String> statusOptions() {
        return List.of(
                PurchaseOrderStatus.DRAFT.apiValue(),
                PurchaseOrderStatus.ORDERED.apiValue(),
                PurchaseOrderStatus.RECEIVED.apiValue(),
                PurchaseOrderStatus.CANCELLED.apiValue());
    }

    private void applyRequest(
            PurchaseOrder order,
            PurchaseOrderRequest request,
            String storeId,
            String ownerPublicUserId,
            boolean creating) {
        SupplierResolution supplierResolution = resolveSupplier(storeId, ownerPublicUserId, request);
        order.setSupplierPublicId(supplierResolution.supplier().getPublicSupplierId());
        order.setSupplierName(supplierResolution.supplier().getName());
        order.setSupplierEmail(supplierResolution.supplier().getEmail());
        order.setSupplierPhone(supplierResolution.supplier().getPhone());
        order.setSupplierCompany(supplierResolution.supplier().getCompany());

        String requestedWarehouse = normalize(request.warehousePublicId());
        if (!creating && requestedWarehouse == null && order.getWarehousePublicId() != null) {
            // keep existing warehouse on update when not explicitly changed
        } else {
            order.setWarehousePublicId(
                    warehouseService.resolveFulfillmentWarehouse(storeId, ownerPublicUserId, requestedWarehouse));
        }

        order.setStatus(request.status() == null ? PurchaseOrderStatus.DRAFT : parseStatus(request.status()));
        order.setOrderDate(request.orderDate() == null ? LocalDate.now() : request.orderDate());
        order.setExpectedDate(request.expectedDate());
        order.setReferenceNumber(normalize(request.referenceNumber()));
        order.setNotes(normalize(request.notes()));
        resolveCurrency(order, storeId);
        applyTags(order, request.tags());
        applyLineItems(order, request.products());
        recalculateTotals(order);
    }

    private SupplierResolution resolveSupplier(
            String storeId, String ownerPublicUserId, PurchaseOrderRequest request) {
        String supplierPublicId = normalize(request.supplierPublicId());
        if (supplierPublicId != null) {
            return new SupplierResolution(requireSupplier(storeId, supplierPublicId), false);
        }
        if (request.supplier() != null) {
            PurchaseSupplier supplier = new PurchaseSupplier();
            supplier.setStoreId(storeId);
            supplier.setOwnerPublicUserId(ownerPublicUserId);
            applySupplierRequest(supplier, request.supplier());
            PurchaseSupplier saved = supplierRepository.save(supplier);
            if (saved.getSupplierCode() == null || saved.getSupplierCode().isBlank()) {
                saved.setSupplierCode(String.format("SUP-%05d", saved.getId()));
                saved = supplierRepository.save(saved);
            }
            return new SupplierResolution(saved, true);
        }
        throw new ResponseStatusException(BAD_REQUEST, "Supplier is required");
    }

    private void applySupplierRequest(PurchaseSupplier supplier, PurchaseSupplierRequest request) {
        String name = normalize(request.name());
        if (name == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Supplier name is required");
        }
        supplier.setName(name);
        supplier.setEmail(normalize(request.email()));
        supplier.setPhone(normalize(request.phone()));
        supplier.setCompany(normalize(request.company()));
        supplier.setAddressLine1(normalize(request.addressLine1()));
        supplier.setAddressLine2(normalize(request.addressLine2()));
        supplier.setCity(normalize(request.city()));
        supplier.setState(normalize(request.state()));
        supplier.setCountry(normalize(request.country()));
        supplier.setPincode(normalize(request.pincode()));
        supplier.setNotes(normalize(request.notes()));
    }

    private void applyLineItems(PurchaseOrder order, List<PurchaseOrderLineItemRequest> products) {
        order.getLineItems().clear();
        if (products == null || products.isEmpty()) {
            return;
        }
        for (PurchaseOrderLineItemRequest request : products) {
            if (request == null) {
                continue;
            }
            String name = normalize(request.name());
            Integer quantity = request.quantity() == null ? 0 : request.quantity();
            BigDecimal costPrice = money(request.costPrice());
            if (name == null || quantity <= 0) {
                continue;
            }
            if (request.productPublicId() != null && !request.productPublicId().isBlank()) {
                Product product = productRepository
                        .findByStoreIdAndPublicProductId(order.getStoreId(), request.productPublicId())
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found"));
                if (product.getTitle() != null && !product.getTitle().isBlank()) {
                    name = request.name() == null || request.name().isBlank() ? product.getTitle() : name;
                }
                if (costPrice.signum() <= 0 && product.getCostPerItem() != null) {
                    costPrice = money(product.getCostPerItem());
                }
            }
            PurchaseOrderLineItem lineItem = new PurchaseOrderLineItem();
            lineItem.setProductPublicId(normalize(request.productPublicId()));
            lineItem.setName(name);
            lineItem.setSku(normalize(request.sku()));
            lineItem.setVariant(normalize(request.variant()));
            lineItem.setQuantity(quantity);
            lineItem.setCostPrice(costPrice);
            lineItem.setLineTotal(money(costPrice.multiply(BigDecimal.valueOf(quantity))));
            lineItem.setImage(normalize(request.image()));
            order.getLineItems().add(lineItem);
        }
    }

    private void applyTags(PurchaseOrder order, List<String> tags) {
        order.getTags().clear();
        if (tags == null) {
            return;
        }
        for (String tag : tags) {
            String normalized = normalize(tag);
            if (normalized != null) {
                order.getTags().add(normalized);
            }
        }
    }

    private void recalculateTotals(PurchaseOrder order) {
        BigDecimal subtotal = order.getLineItems().stream()
                .map(PurchaseOrderLineItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setSubtotal(money(subtotal));
        order.setTotal(money(subtotal));
    }

    private void resolveCurrency(PurchaseOrder order, String storeId) {
        StoreProfile profile = storeProfileRepository.findByStoreId(storeId).orElse(null);
        String code = profile != null && profile.getCurrencyCode() != null ? profile.getCurrencyCode() : "INR";
        order.setCurrencyCode(code);
        order.setCurrencySymbol(resolveSymbol(code));
        order.setCurrencyCountryCode(profile != null && profile.getCountryCode() != null ? profile.getCountryCode() : "IN");
    }

    private void applyInventoryIfReceived(PurchaseOrder order) {
        if (order.getStatus() != PurchaseOrderStatus.RECEIVED || order.isInventoryReceivedApplied()) {
            return;
        }
        Map<String, Integer> quantitiesByProduct = order.getLineItems().stream()
                .filter(item -> item.getProductPublicId() != null && !item.getProductPublicId().isBlank())
                .collect(Collectors.toMap(
                        PurchaseOrderLineItem::getProductPublicId,
                        item -> item.getQuantity() == null ? 0 : item.getQuantity(),
                        Integer::sum));
        if (!quantitiesByProduct.isEmpty()) {
            inventoryService.receive(order.getStoreId(), order.getWarehousePublicId(), quantitiesByProduct);
        }
        order.setInventoryReceivedApplied(true);
        purchaseOrderRepository.save(order);
    }

    private void ensureEditable(PurchaseOrder order) {
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Received purchase orders cannot be edited");
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new ResponseStatusException(BAD_REQUEST, "Cancelled purchase orders cannot be edited");
        }
    }

    private PurchaseOrderStatus parseStatus(String value) {
        try {
            return PurchaseOrderStatus.from(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid purchase order status: " + value);
        }
    }

    private PurchaseOrderStatus parseStatusFilter(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("all")) {
            return null;
        }
        return parseStatus(value);
    }

    private PurchaseOrder require(String storeId, String publicPurchaseOrderId) {
        return purchaseOrderRepository.findByStoreIdAndPublicPurchaseOrderId(storeId, publicPurchaseOrderId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Purchase order not found"));
    }

    private PurchaseSupplier requireSupplier(String storeId, String publicSupplierId) {
        return supplierRepository.findByStoreIdAndPublicSupplierId(storeId, publicSupplierId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Supplier not found"));
    }

    private List<PurchaseOrder> selectedOrders(String storeId, List<String> ids) {
        List<PurchaseOrder> all = purchaseOrderRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        if (ids == null || ids.isEmpty()) {
            return all;
        }
        Set<String> idSet = new HashSet<>(ids);
        return all.stream().filter(order -> idSet.contains(order.getPublicPurchaseOrderId())).toList();
    }

    private PurchaseOrderSummaryData toSummary(PurchaseOrder order) {
        return new PurchaseOrderSummaryData(
                order.getPublicPurchaseOrderId(),
                order.getPurchaseOrderNumber(),
                order.getSupplierPublicId(),
                order.getSupplierName(),
                order.getSupplierEmail(),
                order.getOrderDate(),
                order.getExpectedDate(),
                order.getStatus().apiValue(),
                order.getTotal(),
                itemCount(order),
                order.getWarehousePublicId(),
                order.getReferenceNumber(),
                order.getNotes(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private PurchaseOrderData toData(PurchaseOrder order) {
        return new PurchaseOrderData(
                order.getPublicPurchaseOrderId(),
                order.getPurchaseOrderNumber(),
                order.getSupplierPublicId(),
                findSupplierCode(order),
                order.getSupplierName(),
                order.getSupplierEmail(),
                order.getSupplierPhone(),
                order.getSupplierCompany(),
                order.getWarehousePublicId(),
                order.getStatus().apiValue(),
                order.getOrderDate(),
                order.getExpectedDate(),
                order.getReceivedAt(),
                order.getReferenceNumber(),
                order.getNotes(),
                order.getSubtotal(),
                order.getTotal(),
                itemCount(order),
                order.getLineItems().stream().map(this::toLineItemData).toList(),
                List.copyOf(order.getTags()),
                order.getCurrencyCode(),
                order.getCurrencySymbol(),
                order.getCurrencyCountryCode(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private PurchaseOrderLineItemData toLineItemData(PurchaseOrderLineItem item) {
        return new PurchaseOrderLineItemData(
                item.getId() == null ? null : String.valueOf(item.getId()),
                item.getProductPublicId(),
                item.getName(),
                item.getSku(),
                item.getVariant(),
                item.getQuantity() == null ? 0 : item.getQuantity(),
                item.getCostPrice(),
                item.getLineTotal(),
                item.getImage());
    }

    private PurchaseSupplierData toData(PurchaseSupplier supplier) {
        return new PurchaseSupplierData(
                supplier.getPublicSupplierId(),
                supplier.getSupplierCode(),
                supplier.getName(),
                supplier.getEmail(),
                supplier.getPhone(),
                supplier.getCompany(),
                supplier.getAddressLine1(),
                supplier.getAddressLine2(),
                supplier.getCity(),
                supplier.getState(),
                supplier.getCountry(),
                supplier.getPincode(),
                supplier.getNotes(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt());
    }

    private String nextPurchaseOrderNumber(String storeId) {
        PurchaseOrderNumberSequence sequence = sequenceRepository.findByStoreId(storeId).orElseGet(() -> {
            PurchaseOrderNumberSequence created = new PurchaseOrderNumberSequence();
            created.setStoreId(storeId);
            created.setLastValue(0L);
            return created;
        });
        long next = sequence.getLastValue() == null ? 1L : sequence.getLastValue() + 1L;
        sequence.setLastValue(next);
        PurchaseOrderNumberSequence saved = sequenceRepository.save(sequence);
        return String.format("PO-%04d", saved.getLastValue());
    }

    private String findSupplierCode(PurchaseOrder order) {
        if (order.getSupplierPublicId() == null || order.getSupplierPublicId().isBlank()) {
            return null;
        }
        return supplierRepository.findByStoreIdAndPublicSupplierId(order.getStoreId(), order.getSupplierPublicId())
                .map(PurchaseSupplier::getSupplierCode)
                .orElse(null);
    }

    private static boolean supplierMatches(PurchaseOrder order, String query) {
        return String.join(
                        " ",
                        nullToEmpty(order.getSupplierName()),
                        nullToEmpty(order.getSupplierEmail()),
                        nullToEmpty(order.getSupplierCompany()),
                        nullToEmpty(order.getReferenceNumber()))
                .toLowerCase(Locale.ROOT)
                .contains(query);
    }

    private static String searchable(PurchaseOrder order) {
        Set<String> parts = new LinkedHashSet<>();
        parts.add(order.getPurchaseOrderNumber());
        parts.add(order.getSupplierName());
        parts.add(order.getSupplierEmail());
        parts.add(order.getSupplierPhone());
        parts.add(order.getSupplierCompany());
        parts.add(order.getReferenceNumber());
        parts.add(order.getNotes());
        for (PurchaseOrderLineItem item : order.getLineItems()) {
            parts.add(item.getName());
            parts.add(item.getSku());
            parts.add(item.getVariant());
        }
        return String.join(" ", parts.stream().map(StorePurchaseOrderService::nullToEmpty).toList())
                .toLowerCase(Locale.ROOT);
    }

    private static String searchable(PurchaseSupplier supplier) {
        return String.join(
                        " ",
                        nullToEmpty(supplier.getName()),
                        nullToEmpty(supplier.getEmail()),
                        nullToEmpty(supplier.getPhone()),
                        nullToEmpty(supplier.getCompany()))
                .toLowerCase(Locale.ROOT);
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

    private static int itemCount(PurchaseOrder order) {
        return order.getLineItems().stream().mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum();
    }

    private String resolveSymbol(String currencyCode) {
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
            case "KRW" -> "₩";
            case "BRL" -> "R$";
            case "RUB" -> "₽";
            case "ZAR" -> "R";
            case "CHF" -> "CHF";
            case "MXN" -> "MX$";
            case "SEK", "NOK", "DKK", "ISK" -> "kr";
            case "PLN" -> "zł";
            case "THB" -> "฿";
            case "IDR" -> "Rp";
            case "MYR" -> "RM";
            case "PHP" -> "₱";
            case "VND" -> "₫";
            case "EGP" -> "E£";
            case "NGN" -> "₦";
            case "KES" -> "KSh";
            case "AED" -> "د.إ";
            case "SAR" -> "﷼";
            case "TRY" -> "₺";
            case "PKR" -> "Rs";
            case "BDT" -> "৳";
            case "LKR" -> "Rs";
            case "HKD" -> "HK$";
            case "TWD" -> "NT$";
            case "CZK" -> "Kč";
            case "HUF" -> "Ft";
            case "RON" -> "lei";
            case "BGN" -> "лв";
            case "HRK" -> "kn";
            case "UAH" -> "₴";
            default -> currencyCode;
        };
    }

    private static String csv(String value) {
        String safe = nullToEmpty(value).replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private record SupplierResolution(PurchaseSupplier supplier, boolean created) {}
}
