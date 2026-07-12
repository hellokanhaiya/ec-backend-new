package com.ecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ecommerce.customer.StoreCustomerService;
import com.ecommerce.promotion.StorePromotionService;
import com.ecommerce.store.StoreProfileRepository;
import com.ecommerce.warehouse.StoreInventoryService;
import com.ecommerce.warehouse.StoreWarehouseService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(
        properties = {
            "spring.config.import=",
            "spring.datasource.url=jdbc:h2:mem:orders;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password="
        })
class StoreOrderServiceTest {
    private static final String STORE = "store-1";
    private static final String OWNER = "owner-1";

    @Autowired private StoreOrderRepository orderRepository;

    private StoreOrderService service;
    private StoreInventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = mock(StoreInventoryService.class);
        service = new StoreOrderService(
                orderRepository,
                mock(StoreProfileRepository.class),
                mock(OrderSettingsRepository.class),
                mock(OrderNumberSequenceRepository.class),
                mock(PdfTemplateRepository.class),
                mock(StoreCustomerService.class),
                mock(StoreWarehouseService.class),
                inventoryService,
                mock(StorePromotionService.class));
    }

    @Test
    void createCalculatesDiscountShippingPackageTaxAndTotal() {
        OrderData order = service.create(
                STORE,
                OWNER,
                request(
                        List.of(item("Kurta", "SKU-1", "1000.00", 2, true, "18.00")),
                        new OrderDiscountRequest("coupon", "SAVE10", "percentage", new BigDecimal("10"), "Promo"),
                        new BigDecimal("50.00"),
                        new BigDecimal("25.00")));

        assertThat(order.orderNumber()).startsWith("ORD-");
        assertThat(order.subtotal()).isEqualByComparingTo("2000.00");
        assertThat(order.discount()).isEqualByComparingTo("200.00");
        assertThat(order.shippingCharge()).isEqualByComparingTo("50.00");
        assertThat(order.packageCharge()).isEqualByComparingTo("25.00");
        assertThat(order.tax()).isEqualByComparingTo("324.00");
        assertThat(order.total()).isEqualByComparingTo("2199.00");
        assertThat(order.items()).isEqualTo(2);
    }

    @Test
    void noDefaultShippingChargeIsApplied() {
        // Below the old free-shipping threshold: previously this picked up the
        // store default (99.00); now a missing shipping charge means zero.
        OrderData order = service.create(STORE, OWNER, request(List.of(item("Saree", "SKU-2", "500.00", 1, false, "0")), null, null, null));

        assertThat(order.shippingCharge()).isEqualByComparingTo("0.00");
        assertThat(order.tax()).isEqualByComparingTo("0.00");
        assertThat(order.total()).isEqualByComparingTo("500.00");
    }

    @Test
    void appliesPerItemDiscountToLineTotalAndOrderTotals() {
        OrderData order = service.create(
                STORE,
                OWNER,
                request(
                        List.of(item(
                                "Kurta", "SKU-1", "1000.00", 2, false, "0",
                                new OrderLineItemDiscountRequest("percentage", new BigDecimal("10"), "Loyalty"))),
                        null,
                        null,
                        null));

        assertThat(order.subtotal()).isEqualByComparingTo("1800.00");
        assertThat(order.total()).isEqualByComparingTo("1800.00");
        assertThat(order.products().get(0).discountAmount()).isEqualByComparingTo("200.00");
        assertThat(order.products().get(0).lineTotal()).isEqualByComparingTo("1800.00");
        assertThat(order.products().get(0).discountType()).isEqualTo("percentage");
        assertThat(order.products().get(0).discountReason()).isEqualTo("Loyalty");
    }

    @Test
    void rejectsItemDiscountAboveUnitPrice() {
        assertThatThrownBy(() -> service.create(
                        STORE,
                        OWNER,
                        request(
                                List.of(item(
                                        "Kurta", "SKU-1", "100.00", 1, false, "0",
                                        new OrderLineItemDiscountRequest("fixed", new BigDecimal("150"), null))),
                                null,
                                null,
                                null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("unit price");
    }

    @Test
    void rejectsInvalidEmail() {
        assertThatThrownBy(() -> service.create(
                        STORE,
                        OWNER,
                        request(List.of(item("A", "A", "100.00", 1, false, "0")), null, null, null, "not-an-email")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("email");
    }

    @Test
    void bulkDeleteOnlyDeletesSelectedStoreOrders() {
        OrderData first = service.create(STORE, OWNER, request(List.of(item("A", "A", "100.00", 1, false, "0")), null, null, null));
        OrderData second = service.create(STORE, OWNER, request(List.of(item("B", "B", "100.00", 1, false, "0")), null, null, null));
        service.create("store-2", OWNER, request(List.of(item("C", "C", "100.00", 1, false, "0")), null, null, null));

        int deleted = service.bulkDelete(STORE, List.of(first.id(), "missing"));

        assertThat(deleted).isEqualTo(1);
        assertThat(service.list(STORE, null, null, null, null, null, null, null, 1, 0).items())
                .extracting(OrderSummaryData::id)
                .containsExactly(second.id());
        assertThat(service.list("store-2", null, null, null, null, null, null, null, 1, 0).total()).isEqualTo(1);
    }

    @Test
    void rejectsOrderWithoutProducts() {
        assertThatThrownBy(() -> service.create(STORE, OWNER, request(List.of(), null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("product");
    }

    // --- draft orders -------------------------------------------------------

    @Test
    void draftCanBeSavedWithoutCustomerOrProductsAndSkipsInventory() {
        OrderData draft = service.create(STORE, OWNER, draftRequest(null, List.of(), "Call back Monday"));

        assertThat(draft.status()).isEqualTo("draft");
        assertThat(draft.notes()).isEqualTo("Call back Monday");
        assertThat(draft.items()).isZero();
        verify(inventoryService, never()).deduct(anyString(), any(), any());
    }

    @Test
    void listSeparatesDraftsFromConfirmedOrders() {
        service.create(STORE, OWNER, draftRequest("Draft Customer", List.of(item("A", "A", "100.00", 1, false, "0")), null));
        service.create(STORE, OWNER, request(List.of(item("B", "B", "100.00", 1, false, "0")), null, null, null));

        assertThat(service.list(STORE, null, null, null, null, null, null, null, 1, 0).total()).isEqualTo(1);
        assertThat(service.list(STORE, null, "draft", null, null, null, null, null, 1, 0).total()).isEqualTo(1);
        assertThat(service.list(STORE, null, "all", null, null, null, null, null, 1, 0).total()).isEqualTo(2);
        assertThat(service.overview(STORE).drafts()).isEqualTo(1);
        assertThat(service.overview(STORE).totalOrders()).isEqualTo(1);
    }

    @Test
    void confirmingDraftDeductsInventoryOnce() {
        OrderData draft = service.create(
                STORE, OWNER, draftRequest("Rahul Sharma", List.of(item("A", "A", "100.00", 1, false, "0")), null));
        verify(inventoryService, never()).deduct(anyString(), any(), any());

        OrderData confirmed = service.update(
                STORE,
                draft.id(),
                requestWithStatus(List.of(item("A", "A", "100.00", 1, false, "0")), "confirmed"));

        assertThat(confirmed.status()).isEqualTo("confirmed");
        verify(inventoryService, times(1)).deduct(anyString(), any(), any());
    }

    @Test
    void confirmingDraftWithoutProductsFails() {
        OrderData draft = service.create(STORE, OWNER, draftRequest("Rahul Sharma", List.of(), null));

        assertThatThrownBy(() -> service.update(STORE, draft.id(), requestWithStatus(List.of(), "confirmed")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("product");
    }

    @Test
    void confirmedOrderCannotGoBackToDraft() {
        OrderData order = service.create(STORE, OWNER, request(List.of(item("A", "A", "100.00", 1, false, "0")), null, null, null));

        assertThatThrownBy(() -> service.update(
                        STORE, order.id(), requestWithStatus(List.of(item("A", "A", "100.00", 1, false, "0")), "draft")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("draft");
    }

    private static OrderRequest draftRequest(String customerName, List<OrderLineItemRequest> items, String notes) {
        return new OrderRequest(
                null,
                null,
                null,
                customerName,
                null,
                null,
                null,
                null,
                "draft",
                null,
                null,
                null,
                notes,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                items);
    }

    private static OrderRequest requestWithStatus(List<OrderLineItemRequest> items, String status) {
        return new OrderRequest(
                null,
                null,
                null,
                "Rahul Sharma",
                "rahul@example.com",
                "+919999999999",
                "paid",
                "pending",
                status,
                "Online Store",
                null,
                null,
                "Ship carefully",
                new OrderAddressRequest("A-10", null, "Mumbai", "Maharashtra", "400001", "IN"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("VIP"),
                items);
    }

    private static OrderRequest request(
            List<OrderLineItemRequest> items,
            OrderDiscountRequest discount,
            BigDecimal shippingCharge,
            BigDecimal packageCharge) {
        return request(items, discount, shippingCharge, packageCharge, "rahul@example.com");
    }

    private static OrderRequest request(
            List<OrderLineItemRequest> items,
            OrderDiscountRequest discount,
            BigDecimal shippingCharge,
            BigDecimal packageCharge,
            String email) {
        return new OrderRequest(
                null,
                null,
                null,
                "Rahul Sharma",
                email,
                "+919999999999",
                "paid",
                "pending",
                null,
                "Online Store",
                null,
                null,
                "Ship carefully",
                new OrderAddressRequest("A-10", null, "Mumbai", "Maharashtra", "400001", "IN"),
                discount,
                shippingCharge,
                null,
                packageCharge,
                null,
                null,
                null,
                List.of("VIP"),
                items);
    }

    private static OrderLineItemRequest item(
            String name,
            String sku,
            String price,
            int quantity,
            boolean taxable,
            String taxRate) {
        return item(name, sku, price, quantity, taxable, taxRate, null);
    }

    private static OrderLineItemRequest item(
            String name,
            String sku,
            String price,
            int quantity,
            boolean taxable,
            String taxRate,
            OrderLineItemDiscountRequest discount) {
        return new OrderLineItemRequest(
                null,
                name,
                sku,
                null,
                quantity,
                new BigDecimal(price),
                taxable,
                new BigDecimal(taxRate),
                null,
                discount);
    }
}
