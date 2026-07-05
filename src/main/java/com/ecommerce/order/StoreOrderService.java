package com.ecommerce.order;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.ecommerce.customer.CustomerRequest;
import com.ecommerce.customer.StoreCustomerService;
import com.ecommerce.store.StoreProfile;
import com.ecommerce.store.StoreProfileRepository;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StoreOrderService {
    private static final Map<String, String> CURRENCY_SYMBOLS;

    static {
        CURRENCY_SYMBOLS = new HashMap<>();
        CURRENCY_SYMBOLS.put("INR", "₹");
        CURRENCY_SYMBOLS.put("USD", "$");
        CURRENCY_SYMBOLS.put("EUR", "€");
        CURRENCY_SYMBOLS.put("GBP", "£");
        CURRENCY_SYMBOLS.put("NPR", "रू");
        CURRENCY_SYMBOLS.put("AUD", "A$");
        CURRENCY_SYMBOLS.put("CAD", "C$");
        CURRENCY_SYMBOLS.put("SGD", "S$");
        CURRENCY_SYMBOLS.put("JPY", "¥");
        CURRENCY_SYMBOLS.put("CNY", "¥");
        CURRENCY_SYMBOLS.put("KRW", "₩");
        CURRENCY_SYMBOLS.put("BRL", "R$");
        CURRENCY_SYMBOLS.put("RUB", "₽");
        CURRENCY_SYMBOLS.put("ZAR", "R");
        CURRENCY_SYMBOLS.put("CHF", "CHF");
        CURRENCY_SYMBOLS.put("MXN", "MX$");
        CURRENCY_SYMBOLS.put("SEK", "kr");
        CURRENCY_SYMBOLS.put("NOK", "kr");
        CURRENCY_SYMBOLS.put("DKK", "kr");
        CURRENCY_SYMBOLS.put("PLN", "zł");
        CURRENCY_SYMBOLS.put("THB", "฿");
        CURRENCY_SYMBOLS.put("IDR", "Rp");
        CURRENCY_SYMBOLS.put("MYR", "RM");
        CURRENCY_SYMBOLS.put("PHP", "₱");
        CURRENCY_SYMBOLS.put("VND", "₫");
        CURRENCY_SYMBOLS.put("EGP", "E£");
        CURRENCY_SYMBOLS.put("NGN", "₦");
        CURRENCY_SYMBOLS.put("KES", "KSh");
        CURRENCY_SYMBOLS.put("AED", "د.إ");
        CURRENCY_SYMBOLS.put("SAR", "﷼");
        CURRENCY_SYMBOLS.put("TRY", "₺");
        CURRENCY_SYMBOLS.put("PKR", "₨");
        CURRENCY_SYMBOLS.put("BDT", "৳");
        CURRENCY_SYMBOLS.put("LKR", "Rs");
        CURRENCY_SYMBOLS.put("ISK", "kr");
        CURRENCY_SYMBOLS.put("HKD", "HK$");
        CURRENCY_SYMBOLS.put("TWD", "NT$");
        CURRENCY_SYMBOLS.put("CZK", "Kč");
        CURRENCY_SYMBOLS.put("HUF", "Ft");
        CURRENCY_SYMBOLS.put("RON", "lei");
        CURRENCY_SYMBOLS.put("BGN", "лв");
        CURRENCY_SYMBOLS.put("HRK", "kn");
        CURRENCY_SYMBOLS.put("UAH", "₴");
        CURRENCY_SYMBOLS.put("PHP", "₱");
        CURRENCY_SYMBOLS.put("IDR", "Rp");
        CURRENCY_SYMBOLS.put("MYR", "RM");
        CURRENCY_SYMBOLS.put("VND", "₫");
    }

    private final StoreOrderRepository orderRepository;
    private final StoreProfileRepository storeProfileRepository;
    private final OrderSettingsRepository settingsRepository;
    private final OrderNumberSequenceRepository sequenceRepository;
    private final PdfTemplateRepository templateRepository;
    private final StoreCustomerService customerService;

    public StoreOrderService(
            StoreOrderRepository orderRepository,
            StoreProfileRepository storeProfileRepository,
            OrderSettingsRepository settingsRepository,
            OrderNumberSequenceRepository sequenceRepository,
            PdfTemplateRepository templateRepository,
            StoreCustomerService customerService) {
        this.orderRepository = orderRepository;
        this.storeProfileRepository = storeProfileRepository;
        this.settingsRepository = settingsRepository;
        this.sequenceRepository = sequenceRepository;
        this.templateRepository = templateRepository;
        this.customerService = customerService;
    }

    public OrderListData list(
            String storeId,
            String search,
            String payment,
            String fulfillment,
            String dateFrom,
            String dateTo,
            String customerName,
            int page,
            int size) {
        List<StoreOrder> all = orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String customerQuery = customerName == null ? "" : customerName.trim().toLowerCase(Locale.ROOT);
        PaymentStatus paymentFilter = parsePaymentFilter(payment);
        FulfillmentStatus fulfillmentFilter = parseFulfillmentFilter(fulfillment);
        Instant createdFrom = parseInstant(dateFrom);
        Instant createdTo = parseInstant(dateTo);

        List<StoreOrder> filtered = all.stream()
                .filter(order -> paymentFilter == null || order.getPaymentStatus() == paymentFilter)
                .filter(order -> fulfillmentFilter == null || order.getFulfillmentStatus() == fulfillmentFilter)
                .filter(order -> query.isEmpty() || searchable(order).contains(query))
                .filter(order -> customerQuery.isEmpty() || matchCustomerName(order, customerQuery))
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
        applyRequest(order, request, storeId, ownerPublicUserId);
        order.setOrderNumber(generateOrderNumber(storeId));
        StoreOrder saved = orderRepository.save(order);
        return toData(saved);
    }

    public OrderData update(String storeId, String publicOrderId, OrderRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        StoreOrder order = require(storeId, publicOrderId);
        applyRequest(order, request, storeId, order.getOwnerPublicUserId());
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
        csv.append("Order Number,Customer,Email,Phone,Payment,Fulfillment,Items,Subtotal,Discount,Shipping,Package,Tax,Total,Currency Code,Currency Symbol,Channel,Courier,Tracking,Created At\n");
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
                    .append(csv(order.getCurrencyCode())).append(',')
                    .append(csv(order.getCurrencySymbol())).append(',')
                    .append(csv(order.getChannel())).append(',')
                    .append(csv(order.getCourier())).append(',')
                    .append(csv(order.getTrackingNumber())).append(',')
                    .append(csv(order.getCreatedAt() == null ? null : order.getCreatedAt().toString()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] invoicePdf(String storeId, List<String> ids, String templateId) {
        List<StoreOrder> orders = selectedOrders(storeId, ids);
        PdfTemplate template = resolveTemplate(storeId, templateId, PdfTemplateType.INVOICE);
        return renderInvoicePdf(orders, template);
    }

    public byte[] shippingLabelPdf(String storeId, List<String> ids, String templateId) {
        List<StoreOrder> orders = selectedOrders(storeId, ids);
        PdfTemplate template = resolveTemplate(storeId, templateId, PdfTemplateType.SHIPPING_LABEL);
        return renderShippingLabelPdf(orders, template);
    }

    // --- private helpers ---------------------------------------------------

    private void applyRequest(StoreOrder order, OrderRequest request, String storeId, String ownerPublicUserId) {
        String customerName = normalize(request.customerName());
        if (customerName == null && request.newCustomer() == null && request.customerPublicId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Customer name, customerPublicId, or newCustomer is required");
        }

        // Inline customer creation
        if (request.newCustomer() != null) {
            InlineCustomerRequest nc = request.newCustomer();
            String firstName = normalize(nc.firstName());
            if (firstName == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Customer first name is required");
            }
            CustomerRequest custReq = new CustomerRequest(
                    firstName,
                    normalize(nc.lastName()),
                    normalize(nc.email()),
                    normalize(nc.phoneCountryCode()),
                    normalize(nc.phone()),
                    false, false, false, false,
                    "ACTIVE",
                    null, null, null,
                    null, null, null);
            var customerData = customerService.create(storeId, ownerPublicUserId, custReq);
            order.setCustomerPublicId(customerData.publicCustomerId());
            if (customerName == null) {
                customerName = (firstName + " " + nullToEmpty(normalize(nc.lastName()))).trim();
            }
            if (order.getEmail() == null) {
                order.setEmail(normalize(nc.email()));
            }
            if (order.getPhone() == null) {
                order.setPhone(normalize(nc.phone()));
            }
        } else {
            order.setCustomerPublicId(normalize(request.customerPublicId()));
        }

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

        // Currency resolution: request > store profile > defaults
        resolveCurrency(order, request, storeId);

        applyDiscount(order, request.discount());
        applyTags(order, request.tags());
        applyLineItems(order, request.products());
        recalculateTotals(order, request.shippingCharge(), request.packageCharge(), storeId);
    }

    private void resolveCurrency(StoreOrder order, OrderRequest request, String storeId) {
        String code = normalize(request.currencyCode());
        String symbol = normalize(request.currencySymbol());
        String country = normalize(request.currencyCountryCode());

        if (code == null) {
            storeProfileRepository.findByStoreId(storeId).ifPresent(profile -> {
                order.setCurrencyCode(profile.getCurrencyCode());
                order.setCurrencySymbol(resolveSymbol(profile.getCurrencyCode()));
                order.setCurrencyCountryCode(profile.getCountryCode());
            });
            if (order.getCurrencyCode() == null) {
                order.setCurrencyCode("INR");
                order.setCurrencySymbol("₹");
                order.setCurrencyCountryCode("IN");
            }
        } else {
            order.setCurrencyCode(code);
            order.setCurrencySymbol(symbol != null ? symbol : resolveSymbol(code));
            order.setCurrencyCountryCode(country != null ? country : "IN");
        }
    }

    private String resolveSymbol(String currencyCode) {
        if (currencyCode == null) {
            return "₹";
        }
        return CURRENCY_SYMBOLS.getOrDefault(currencyCode.toUpperCase(Locale.ROOT), currencyCode);
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
            item.setTaxRate(request.taxRate() == null ? getDefaultTaxRate(order.getStoreId()) : request.taxRate());
            item.setImage(normalize(request.image()));
            order.getLineItems().add(item);
        }
        if (order.getLineItems().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one product is required");
        }
    }

    private void recalculateTotals(StoreOrder order, BigDecimal requestedShipping, BigDecimal requestedPackage, String storeId) {
        OrderSettings settings = getSettings(storeId);
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
            BigDecimal rate = item.getTaxRate() == null ? settings.getDefaultTaxRate() : item.getTaxRate();
            tax = tax.add(taxableBase.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
        }
        BigDecimal shipping = requestedShipping == null
                ? defaultShipping(discountedSubtotal, settings)
                : requestedShipping;
        BigDecimal packageCharge = requestedPackage == null ? settings.getDefaultPackageCharge() : requestedPackage;
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

    /**
     * Allocates the next order number for the store's current period. The counter row is
     * locked for update so concurrent creates serialise and never share a number; the
     * counter restarts at 1 each financial year when {@code financialYearReset} is on.
     */
    private String generateOrderNumber(String storeId) {
        OrderSettings settings = getSettings(storeId);
        Instant now = Instant.now();
        String periodKey = OrderNumberFormatter.periodKey(settings, now);
        OrderNumberSequence sequence = sequenceRepository
                .lockByStoreIdAndPeriodKey(storeId, periodKey)
                .orElseGet(() -> {
                    OrderNumberSequence fresh = new OrderNumberSequence();
                    fresh.setStoreId(storeId);
                    fresh.setPeriodKey(periodKey);
                    fresh.setLastValue(0L);
                    return fresh;
                });
        long next = sequence.getLastValue() + 1;
        sequence.setLastValue(next);
        sequenceRepository.save(sequence);
        return OrderNumberFormatter.format(settings, now, next);
    }

    private OrderSettings getSettings(String storeId) {
        return settingsRepository.findByStoreId(storeId).orElseGet(() -> {
            OrderSettings defaults = new OrderSettings();
            defaults.setStoreId(storeId);
            return defaults;
        });
    }

    private BigDecimal getDefaultTaxRate(String storeId) {
        return getSettings(storeId).getDefaultTaxRate();
    }

    private PdfTemplate resolveTemplate(String storeId, String templateId, PdfTemplateType type) {
        if (templateId != null && !templateId.isBlank()) {
            return templateRepository.findByStoreIdAndPublicTemplateId(storeId, templateId)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Template not found"));
        }
        PdfTemplate defaultTemplate = templateRepository.findByStoreIdAndTypeAndDefaultTemplateTrue(storeId, type)
                .orElse(null);
        if (defaultTemplate != null) {
            return defaultTemplate;
        }
        List<PdfTemplate> templates = templateRepository.findByStoreIdAndTypeOrderByCreatedAtDesc(storeId, type);
        return templates.isEmpty() ? null : templates.get(0);
    }

    // --- PDF rendering ------------------------------------------------------

    private byte[] renderInvoicePdf(List<StoreOrder> orders, PdfTemplate template) {
        String accentColor = "#000000";
        boolean showBorders = true;
        boolean showGrid = true;
        String style = "classic";

        if (template != null && template.getLayoutConfig() != null) {
            accentColor = extractJsonString(template.getLayoutConfig(), "accentColor", "#000000");
            showBorders = extractJsonBool(template.getLayoutConfig(), "showBorders", true);
            showGrid = extractJsonBool(template.getLayoutConfig(), "showGrid", true);
            style = extractJsonString(template.getLayoutConfig(), "style", "classic");
        }

        StringBuilder content = new StringBuilder();
        int y = 760;

        // Header
        content.append("BT /F1 18 Tf ").append(accentColorRgb(accentColor)).append(" 50 ").append(y).append(" Td ");
        content.append("(Order Invoices) Tj ET ");
        y -= 30;

        for (StoreOrder order : orders) {
            if (y < 100) {
                break;
            }

            // Order number and date
            content.append("BT /F1 12 Tf 50 ").append(y).append(" Td ");
            content.append("(").append(pdf(order.getOrderNumber() == null ? order.getPublicOrderId() : order.getOrderNumber())).append(") Tj ET ");
            y -= 16;

            // Customer info
            content.append("BT /F1 10 Tf 50 ").append(y).append(" Td ");
            content.append("(").append(pdf(order.getCustomerName())).append(") Tj ET ");
            y -= 14;

            // Currency-aware total
            String currencySymbol = order.getCurrencySymbol() != null ? order.getCurrencySymbol() : "₹";
            content.append("BT /F1 10 Tf 50 ").append(y).append(" Td ");
            content.append("(").append(pdf("Total: " + currencySymbol + order.getTotal())).append(") Tj ET ");
            y -= 14;

            // Payment and fulfillment
            content.append("BT /F1 10 Tf 50 ").append(y).append(" Td ");
            content.append("(").append(pdf("Payment: " + order.getPaymentStatus().apiValue()
                    + "  |  Fulfillment: " + order.getFulfillmentStatus().apiValue())).append(") Tj ET ");
            y -= 16;

            // Line items
            if (showGrid) {
                content.append("BT /F1 8 Tf 60 ").append(y).append(" Td ");
                content.append("(Product                                                          Qty    Price    Total) Tj ET ");
                y -= 12;

                for (StoreOrderLineItem item : order.getLineItems()) {
                    if (y < 100) break;
                    String line = String.format("%-60s %4d  %s%8s  %s%8s",
                            truncate(item.getName(), 60),
                            item.getQuantity(),
                            currencySymbol,
                            item.getUnitPrice(),
                            currencySymbol,
                            item.getLineTotal());
                    content.append("BT /F1 8 Tf 60 ").append(y).append(" Td ");
                    content.append("(").append(pdf(line)).append(") Tj ET ");
                    y -= 11;
                }
            }

            // Totals breakdown
            y -= 4;
            content.append("BT /F1 9 Tf 50 ").append(y).append(" Td ");
            content.append("(").append(pdf("Subtotal: " + currencySymbol + order.getSubtotal()
                    + "  Discount: " + currencySymbol + order.getDiscountAmount()
                    + "  Shipping: " + currencySymbol + order.getShippingCharge()
                    + "  Package: " + currencySymbol + order.getPackageCharge()
                    + "  Tax: " + currencySymbol + order.getTaxAmount())).append(") Tj ET ");
            y -= 20;

            if (showBorders) {
                content.append("0.5 0.5 0.5 rg 50 ").append(y).append(" 512 0 re S ");
                y -= 8;
            }
        }

        return buildPdf(content.toString());
    }

    private byte[] renderShippingLabelPdf(List<StoreOrder> orders, PdfTemplate template) {
        StringBuilder content = new StringBuilder();
        int y = 760;

        content.append("BT /F1 18 Tf 50 ").append(y).append(" Td ");
        content.append("(Shipping Labels) Tj ET ");
        y -= 30;

        for (StoreOrder order : orders) {
            if (y < 120) {
                break;
            }

            // Ship To header
            content.append("BT /F1 12 Tf 50 ").append(y).append(" Td ");
            content.append("(SHIP TO: ").append(pdf(order.getCustomerName())).append(") Tj ET ");
            y -= 16;

            content.append("BT /F1 10 Tf 50 ").append(y).append(" Td ");
            content.append("(").append(pdf(nullToEmpty(order.getAddressLine1()))).append(") Tj ET ");
            y -= 14;

            if (order.getAddressLine2() != null && !order.getAddressLine2().isBlank()) {
                content.append("BT /F1 10 Tf 50 ").append(y).append(" Td ");
                content.append("(").append(pdf(order.getAddressLine2())).append(") Tj ET ");
                y -= 14;
            }

            String cityLine = String.join(
                    ", ",
                    nonBlank(order.getCity()),
                    nonBlank(order.getState()),
                    nonBlank(order.getPincode()));
            content.append("BT /F1 10 Tf 50 ").append(y).append(" Td ");
            content.append("(").append(pdf(cityLine)).append(") Tj ET ");
            y -= 14;

            content.append("BT /F1 10 Tf 50 ").append(y).append(" Td ");
            content.append("(Phone: ").append(pdf(nullToEmpty(order.getPhone()))).append(") Tj ET ");
            y -= 14;

            content.append("BT /F1 10 Tf 50 ").append(y).append(" Td ");
            content.append("(Courier: ").append(pdf(nullToEmpty(order.getCourier()))).append(") Tj ET ");
            y -= 14;

            content.append("BT /F1 10 Tf 50 ").append(y).append(" Td ");
            content.append("(Tracking: ").append(pdf(nullToEmpty(order.getTrackingNumber()))).append(") Tj ET ");
            y -= 20;
        }

        return buildPdf(content.toString());
    }

    private byte[] buildPdf(String contentStream) {
        byte[] stream = contentStream.getBytes(StandardCharsets.UTF_8);

        List<String> objects = List.of(
                "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n",
                "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n",
                "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n",
                "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n",
                "5 0 obj << /Length " + stream.length + " >> stream\n" + contentStream + "\nendstream endobj\n");
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

    private String accentColorRgb(String hex) {
        if (hex == null || hex.length() < 7) {
            return "0 0 0 rg";
        }
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            return String.format(Locale.ROOT, "%.3f %.3f %.3f rg", r / 255.0, g / 255.0, b / 255.0);
        } catch (Exception e) {
            return "0 0 0 rg";
        }
    }

    private static String extractJsonString(String json, String key, String defaultValue) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return defaultValue;
        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx < 0) return defaultValue;
        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart < 0) return defaultValue;
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) return defaultValue;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    private static boolean extractJsonBool(String json, String key, boolean defaultValue) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return defaultValue;
        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx < 0) return defaultValue;
        String after = json.substring(colonIdx + 1).trim();
        return after.startsWith("true");
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    // --- data mapping -------------------------------------------------------

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
                toAddress(order),
                order.getCurrencyCode(),
                order.getCurrencySymbol(),
                order.getCurrencyCountryCode());
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
                toAddress(order),
                order.getCurrencyCode(),
                order.getCurrencySymbol(),
                order.getCurrencyCountryCode());
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

    private static boolean matchCustomerName(StoreOrder order, String query) {
        String name = order.getCustomerName();
        return name != null && name.toLowerCase(Locale.ROOT).contains(query);
    }

    private static BigDecimal defaultShipping(BigDecimal discountedSubtotal, OrderSettings settings) {
        if (discountedSubtotal.signum() <= 0 || discountedSubtotal.compareTo(settings.getFreeShippingThreshold()) > 0) {
            return BigDecimal.ZERO;
        }
        return settings.getDefaultShippingCharge();
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

    private static String pdf(String value) {
        return nullToEmpty(value).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
