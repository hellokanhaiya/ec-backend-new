package com.ecommerce.order;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StoreOrderService {
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("4999.00");
    private static final BigDecimal DEFAULT_SHIPPING_CHARGE = new BigDecimal("99.00");
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("18.00");

    private final StoreOrderRepository orderRepository;

    public StoreOrderService(StoreOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderListData list(
            String storeId,
            String search,
            String payment,
            String fulfillment,
            String dateFrom,
            String dateTo,
            int page,
            int size) {
        List<StoreOrder> all = orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        PaymentStatus paymentFilter = parsePaymentFilter(payment);
        FulfillmentStatus fulfillmentFilter = parseFulfillmentFilter(fulfillment);
        Instant createdFrom = parseInstant(dateFrom);
        Instant createdTo = parseInstant(dateTo);

        List<StoreOrder> filtered = all.stream()
                .filter(order -> paymentFilter == null || order.getPaymentStatus() == paymentFilter)
                .filter(order -> fulfillmentFilter == null || order.getFulfillmentStatus() == fulfillmentFilter)
                .filter(order -> query.isEmpty() || searchable(order).contains(query))
                .filter(order -> withinRange(order.getCreatedAt(), createdFrom, createdTo))
                .toList();

        long total = filtered.size();
        List<StoreOrder> pageItems;
        if (size <= 0) {
            pageItems = filtered;
        } else {
            int from = Math.max(0, (Math.max(page, 1) - 1) * size);
            int to = Math.min(filtered.size(), from + size);
            pageItems = from >= filtered.size() ? List.of() : filtered.subList(from, to);
        }

        return new OrderListData(pageItems.stream().map(this::toSummary).toList(), total, Math.max(page, 1), size);
    }

    public OrderOverviewData overview(String storeId) {
        List<StoreOrder> all = orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        long pendingPayment = all.stream().filter(order -> order.getPaymentStatus() != PaymentStatus.PAID).count();
        long readyToShip = all.stream()
                .filter(order -> order.getPaymentStatus() == PaymentStatus.PAID)
                .filter(order -> order.getFulfillmentStatus() == FulfillmentStatus.PENDING
                        || order.getFulfillmentStatus() == FulfillmentStatus.READY_TO_SHIP)
                .count();
        long shipped = all.stream().filter(order -> order.getFulfillmentStatus() == FulfillmentStatus.SHIPPED).count();
        long delivered = all.stream()
                .filter(order -> order.getFulfillmentStatus() == FulfillmentStatus.FULFILLED
                        || order.getFulfillmentStatus() == FulfillmentStatus.DELIVERED)
                .count();
        long returns = all.stream()
                .filter(order -> order.getFulfillmentStatus() == FulfillmentStatus.RETURNED
                        || order.getPaymentStatus() == PaymentStatus.REFUNDED
                        || order.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED)
                .count();
        BigDecimal revenue = all.stream()
                .filter(order -> order.getPaymentStatus() == PaymentStatus.PAID)
                .map(StoreOrder::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderOverviewData(all.size(), pendingPayment, readyToShip, shipped, delivered, returns, money(revenue));
    }

    public OrderData get(String storeId, String publicOrderId) {
        return toData(require(storeId, publicOrderId));
    }

    public OrderData create(String storeId, String ownerPublicUserId, OrderRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        StoreOrder order = new StoreOrder();
        order.setStoreId(storeId);
        order.setOwnerPublicUserId(ownerPublicUserId);
        applyRequest(order, request);
        StoreOrder saved = orderRepository.save(order);
        if (saved.getOrderNumber() == null || saved.getOrderNumber().isBlank()) {
            saved.setOrderNumber(String.format("ORD-%05d", saved.getId()));
            saved = orderRepository.save(saved);
        }
        return toData(saved);
    }

    public OrderData update(String storeId, String publicOrderId, OrderRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        StoreOrder order = require(storeId, publicOrderId);
        applyRequest(order, request);
        return toData(orderRepository.save(order));
    }

    public void delete(String storeId, String publicOrderId) {
        orderRepository.delete(require(storeId, publicOrderId));
    }

    public int bulkDelete(String storeId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Set<String> idSet = new HashSet<>(ids);
        List<StoreOrder> toDelete = orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .filter(order -> idSet.contains(order.getPublicOrderId()))
                .toList();
        orderRepository.deleteAll(toDelete);
        return toDelete.size();
    }

    public byte[] exportCsv(String storeId, List<String> ids) {
        StringBuilder csv = new StringBuilder();
        csv.append("Order Number,Customer,Email,Phone,Payment,Fulfillment,Items,Subtotal,Discount,Shipping,Package,Tax,Total,Channel,Courier,Tracking,Created At\n");
        for (StoreOrder order : selectedOrders(storeId, ids)) {
            csv.append(csv(order.getOrderNumber())).append(',')
                    .append(csv(order.getCustomerName())).append(',')
                    .append(csv(order.getEmail())).append(',')
                    .append(csv(order.getPhone())).append(',')
                    .append(csv(order.getPaymentStatus().apiValue())).append(',')
                    .append(csv(order.getFulfillmentStatus().apiValue())).append(',')
                    .append(order.getLineItems().stream().mapToInt(StoreOrderLineItem::getQuantity).sum()).append(',')
                    .append(order.getSubtotal()).append(',')
                    .append(order.getDiscountAmount()).append(',')
                    .append(order.getShippingCharge()).append(',')
                    .append(order.getPackageCharge()).append(',')
                    .append(order.getTaxAmount()).append(',')
                    .append(order.getTotal()).append(',')
                    .append(csv(order.getChannel())).append(',')
                    .append(csv(order.getCourier())).append(',')
                    .append(csv(order.getTrackingNumber())).append(',')
                    .append(csv(order.getCreatedAt() == null ? null : order.getCreatedAt().toString()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] invoicePdf(String storeId, List<String> ids) {
        List<String> lines = new ArrayList<>();
        lines.add("Order Invoices");
        lines.add("");
        for (StoreOrder order : selectedOrders(storeId, ids)) {
            lines.add((order.getOrderNumber() == null ? order.getPublicOrderId() : order.getOrderNumber())
                    + " | " + order.getCustomerName() + " | Total: " + order.getTotal());
            lines.add("Payment: " + order.getPaymentStatus().apiValue()
                    + " | Fulfillment: " + order.getFulfillmentStatus().apiValue());
            for (StoreOrderLineItem item : order.getLineItems()) {
                lines.add("  " + item.getName() + " x " + item.getQuantity() + " @ " + item.getUnitPrice()
                        + " = " + item.getLineTotal());
            }
            lines.add("Subtotal: " + order.getSubtotal() + " Discount: " + order.getDiscountAmount()
                    + " Shipping: " + order.getShippingCharge() + " Package: " + order.getPackageCharge()
                    + " Tax: " + order.getTaxAmount());
            lines.add("");
        }
        return simplePdf(lines);
    }

    public byte[] shippingLabelPdf(String storeId, List<String> ids) {
        List<String> lines = new ArrayList<>();
        lines.add("Shipping Labels");
        lines.add("");
        for (StoreOrder order : selectedOrders(storeId, ids)) {
            lines.add("SHIP TO: " + order.getCustomerName());
            lines.add(nullToEmpty(order.getAddressLine1()));
            if (order.getAddressLine2() != null && !order.getAddressLine2().isBlank()) {
                lines.add(order.getAddressLine2());
            }
            lines.add(String.join(
                    ", ",
                    nonBlank(order.getCity()),
                    nonBlank(order.getState()),
                    nonBlank(order.getPincode())));
            lines.add("Phone: " + nullToEmpty(order.getPhone()));
            lines.add("Courier: " + nullToEmpty(order.getCourier()));
            lines.add("Tracking: " + nullToEmpty(order.getTrackingNumber()));
            lines.add("");
        }
        return simplePdf(lines);
    }

    private void applyRequest(StoreOrder order, OrderRequest request) {
        String customerName = normalize(request.customerName());
        if (customerName == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Customer name is required");
        }
        order.setCustomerPublicId(normalize(request.customerPublicId()));
        order.setCustomerName(customerName);
        order.setEmail(normalize(request.email()));
        order.setPhone(normalize(request.phone()));
        order.setPaymentStatus(parsePayment(request.payment()));
        order.setFulfillmentStatus(parseFulfillment(request.fulfillment()));
        order.setChannel(firstNonBlank(request.channel(), "Online Store"));
        order.setCourier(normalize(request.courier()));
        order.setTrackingNumber(normalize(request.trackingNumber()));
        order.setNotes(normalize(request.notes()));

        OrderAddressRequest address = request.address();
        order.setAddressLine1(address == null ? null : normalize(address.line1()));
        order.setAddressLine2(address == null ? null : normalize(address.line2()));
        order.setCity(address == null ? null : normalize(address.city()));
        order.setState(address == null ? null : normalize(address.state()));
        order.setPincode(address == null ? null : normalize(address.pincode()));
        order.setCountry(address == null ? null : normalize(address.country()));

        applyDiscount(order, request.discount());
        applyTags(order, request.tags());
        applyLineItems(order, request.products());
        recalculateTotals(order, request.shippingCharge(), request.packageCharge());
    }

    private void applyDiscount(StoreOrder order, OrderDiscountRequest discount) {
        if (discount == null) {
            order.setDiscountMode(DiscountMode.MANUAL);
            order.setDiscountType(DiscountType.FIXED);
            order.setDiscountValue(BigDecimal.ZERO);
            order.setCouponCode(null);
            order.setDiscountReason(null);
            return;
        }
        order.setDiscountMode(parseDiscountMode(discount.mode()));
        order.setDiscountType(parseDiscountType(discount.type()));
        BigDecimal value = discount.value() == null ? BigDecimal.ZERO : discount.value();
        if (value.signum() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Discount value cannot be negative");
        }
        if (order.getDiscountType() == DiscountType.PERCENTAGE && value.compareTo(new BigDecimal("100")) > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Percentage discount cannot exceed 100");
        }
        order.setDiscountValue(money(value));
        order.setCouponCode(normalize(discount.code()));
        order.setDiscountReason(normalize(discount.reason()));
    }

    private void applyTags(StoreOrder order, List<String> tags) {
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

    private void applyLineItems(StoreOrder order, List<OrderLineItemRequest> requests) {
        order.getLineItems().clear();
        if (requests == null || requests.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one product is required");
        }
        for (OrderLineItemRequest request : requests) {
            if (request == null) {
                continue;
            }
            String name = normalize(request.name());
            if (name == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Product name is required");
            }
            int quantity = request.quantity() == null ? 1 : request.quantity();
            if (quantity <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Product quantity must be greater than zero");
            }
            BigDecimal price = request.price() == null ? BigDecimal.ZERO : request.price();
            if (price.signum() < 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Product price cannot be negative");
            }
            StoreOrderLineItem item = new StoreOrderLineItem();
            item.setProductPublicId(normalize(request.productPublicId()));
            item.setName(name);
            item.setSku(normalize(request.sku()));
            item.setVariant(normalize(request.variant()));
            item.setQuantity(quantity);
            item.setUnitPrice(money(price));
            item.setLineTotal(money(price.multiply(BigDecimal.valueOf(quantity))));
            item.setTaxable(request.taxable() == null || request.taxable());
            item.setTaxRate(request.taxRate() == null ? DEFAULT_TAX_RATE : request.taxRate());
            item.setImage(normalize(request.image()));
            order.getLineItems().add(item);
        }
        if (order.getLineItems().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one product is required");
        }
    }

    private void recalculateTotals(StoreOrder order, BigDecimal requestedShipping, BigDecimal requestedPackage) {
        BigDecimal subtotal = order.getLineItems().stream()
                .map(StoreOrderLineItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountAmount = order.getDiscountType() == DiscountType.PERCENTAGE
                ? subtotal.multiply(order.getDiscountValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : order.getDiscountValue();
        if (discountAmount.compareTo(subtotal) > 0) {
            discountAmount = subtotal;
        }
        BigDecimal discountedSubtotal = subtotal.subtract(discountAmount);
        BigDecimal tax = BigDecimal.ZERO;
        for (StoreOrderLineItem item : order.getLineItems()) {
            if (!item.isTaxable()) {
                continue;
            }
            BigDecimal lineDiscountShare = subtotal.signum() == 0
                    ? BigDecimal.ZERO
                    : item.getLineTotal()
                            .divide(subtotal, 8, RoundingMode.HALF_UP)
                            .multiply(discountAmount);
            BigDecimal taxableBase = item.getLineTotal().subtract(lineDiscountShare).max(BigDecimal.ZERO);
            BigDecimal rate = item.getTaxRate() == null ? DEFAULT_TAX_RATE : item.getTaxRate();
            tax = tax.add(taxableBase.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
        }
        BigDecimal shipping = requestedShipping == null ? defaultShipping(discountedSubtotal) : requestedShipping;
        BigDecimal packageCharge = requestedPackage == null ? BigDecimal.ZERO : requestedPackage;
        if (shipping.signum() < 0 || packageCharge.signum() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Shipping and package charges cannot be negative");
        }
        order.setSubtotal(money(subtotal));
        order.setDiscountAmount(money(discountAmount));
        order.setShippingCharge(money(shipping));
        order.setPackageCharge(money(packageCharge));
        order.setTaxAmount(money(tax));
        order.setTotal(money(discountedSubtotal.add(shipping).add(packageCharge).add(tax)));
    }

    private StoreOrder require(String storeId, String publicOrderId) {
        return orderRepository.findByStoreIdAndPublicOrderId(storeId, publicOrderId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found"));
    }

    private List<StoreOrder> selectedOrders(String storeId, List<String> ids) {
        List<StoreOrder> all = orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        if (ids == null || ids.isEmpty()) {
            return all;
        }
        Set<String> idSet = new HashSet<>(ids);
        return all.stream().filter(order -> idSet.contains(order.getPublicOrderId())).toList();
    }

    private OrderData toData(StoreOrder order) {
        return new OrderData(
                order.getPublicOrderId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getCustomerPublicId(),
                order.getEmail(),
                order.getPhone(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getTotal(),
                order.getSubtotal(),
                order.getTaxAmount(),
                order.getDiscountAmount(),
                order.getShippingCharge(),
                order.getPackageCharge(),
                order.getPaymentStatus().apiValue(),
                order.getPaymentStatus().apiValue(),
                order.getFulfillmentStatus().apiValue(),
                order.getFulfillmentStatus().apiValue(),
                itemCount(order),
                order.getLineItems().stream().map(this::toLineItemData).toList(),
                List.copyOf(order.getTags()),
                order.getChannel(),
                order.getCourier(),
                order.getTrackingNumber(),
                timeline(order),
                order.getNotes(),
                toAddress(order));
    }

    private OrderSummaryData toSummary(StoreOrder order) {
        return new OrderSummaryData(
                order.getPublicOrderId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getEmail(),
                order.getPhone(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getTotal(),
                order.getSubtotal(),
                order.getTaxAmount(),
                order.getDiscountAmount(),
                order.getPaymentStatus().apiValue(),
                order.getPaymentStatus().apiValue(),
                order.getFulfillmentStatus().apiValue(),
                order.getFulfillmentStatus().apiValue(),
                itemCount(order),
                order.getLineItems().stream().map(this::toLineItemData).toList(),
                List.copyOf(order.getTags()),
                order.getChannel(),
                order.getCourier(),
                order.getTrackingNumber(),
                order.getNotes(),
                toAddress(order));
    }

    private OrderLineItemData toLineItemData(StoreOrderLineItem item) {
        return new OrderLineItemData(
                item.getId() == null ? null : String.valueOf(item.getId()),
                item.getProductPublicId(),
                item.getName(),
                item.getSku(),
                item.getVariant(),
                item.getQuantity() == null ? 0 : item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal(),
                item.isTaxable(),
                item.getTaxRate(),
                item.getImage());
    }

    private OrderAddressData toAddress(StoreOrder order) {
        return new OrderAddressData(
                order.getAddressLine1(),
                order.getAddressLine2(),
                order.getCity(),
                order.getState(),
                order.getPincode(),
                order.getCountry());
    }

    private List<OrderTimelineData> timeline(StoreOrder order) {
        List<OrderTimelineData> timeline = new ArrayList<>();
        timeline.add(new OrderTimelineData("Order placed", displayTime(order.getCreatedAt()), "Order created in admin"));
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            timeline.add(new OrderTimelineData("Payment received", displayTime(order.getUpdatedAt()), "Payment marked as paid"));
        }
        if (order.getFulfillmentStatus() == FulfillmentStatus.SHIPPED) {
            timeline.add(new OrderTimelineData("Order shipped", displayTime(order.getUpdatedAt()), "Package handed to courier"));
        }
        if (order.getFulfillmentStatus() == FulfillmentStatus.FULFILLED
                || order.getFulfillmentStatus() == FulfillmentStatus.DELIVERED) {
            timeline.add(new OrderTimelineData("Order delivered", displayTime(order.getUpdatedAt()), "Order marked complete"));
        }
        return timeline;
    }

    private String searchable(StoreOrder order) {
        Set<String> parts = new LinkedHashSet<>();
        parts.add(order.getOrderNumber());
        parts.add(order.getCustomerName());
        parts.add(order.getEmail());
        parts.add(order.getPhone());
        parts.add(order.getCourier());
        parts.add(order.getTrackingNumber());
        parts.add(order.getChannel());
        for (StoreOrderLineItem item : order.getLineItems()) {
            parts.add(item.getName());
            parts.add(item.getSku());
        }
        return String.join(" ", parts.stream().map(StoreOrderService::nullToEmpty).toList()).toLowerCase(Locale.ROOT);
    }

    private static BigDecimal defaultShipping(BigDecimal discountedSubtotal) {
        if (discountedSubtotal.signum() <= 0 || discountedSubtotal.compareTo(FREE_SHIPPING_THRESHOLD) > 0) {
            return BigDecimal.ZERO;
        }
        return DEFAULT_SHIPPING_CHARGE;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static PaymentStatus parsePayment(String value) {
        try {
            return PaymentStatus.from(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid payment status: " + value);
        }
    }

    private static PaymentStatus parsePaymentFilter(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("all")) {
            return null;
        }
        return parsePayment(value);
    }

    private static FulfillmentStatus parseFulfillment(String value) {
        try {
            return FulfillmentStatus.from(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid fulfillment status: " + value);
        }
    }

    private static FulfillmentStatus parseFulfillmentFilter(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("all")) {
            return null;
        }
        return parseFulfillment(value);
    }

    private static DiscountMode parseDiscountMode(String value) {
        try {
            return DiscountMode.from(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid discount mode: " + value);
        }
    }

    private static DiscountType parseDiscountType(String value) {
        try {
            return DiscountType.from(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid discount type: " + value);
        }
    }

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

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String firstNonBlank(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String nonBlank(String value) {
        return value == null ? "" : value;
    }

    private static int itemCount(StoreOrder order) {
        return order.getLineItems().stream().mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum();
    }

    private static String displayTime(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private static String csv(String value) {
        String safe = nullToEmpty(value).replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private static byte[] simplePdf(List<String> lines) {
        StringBuilder content = new StringBuilder();
        content.append("BT /F1 12 Tf 50 780 Td ");
        int count = 0;
        for (String line : lines) {
            if (count > 0) {
                content.append("0 -16 Td ");
            }
            if (count > 45) {
                content.append("(...) Tj ");
                break;
            }
            content.append('(').append(pdf(line)).append(") Tj ");
            count++;
        }
        content.append("ET");
        byte[] stream = content.toString().getBytes(StandardCharsets.UTF_8);

        List<String> objects = List.of(
                "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n",
                "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n",
                "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n",
                "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n",
                "5 0 obj << /Length " + stream.length + " >> stream\n" + content + "\nendstream endobj\n");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (String object : objects) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.UTF_8).length);
            pdf.append(object);
        }
        int xrefOffset = pdf.toString().getBytes(StandardCharsets.UTF_8).length;
        pdf.append("xref\n0 6\n0000000000 65535 f \n");
        for (Integer offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer << /Size 6 /Root 1 0 R >>\nstartxref\n")
                .append(xrefOffset)
                .append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String pdf(String value) {
        return nullToEmpty(value).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
