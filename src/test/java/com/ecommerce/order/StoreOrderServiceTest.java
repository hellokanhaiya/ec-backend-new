package com.ecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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

    @BeforeEach
    void setUp() {
        service = new StoreOrderService(
                orderRepository,
                mock(StoreProfileRepository.class),
                mock(OrderSettingsRepository.class),
                mock(OrderNumberSequenceRepository.class),
                mock(PdfTemplateRepository.class),
                mock(StoreCustomerService.class),
                mock(StoreWarehouseService.class),
                mock(StoreInventoryService.class),
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
    void defaultShippingIsFreeAboveThreshold() {
        OrderData order = service.create(STORE, OWNER, request(List.of(item("Saree", "SKU-2", "6000.00", 1, false, "0")), null, null, null));

        assertThat(order.shippingCharge()).isEqualByComparingTo("0.00");
        assertThat(order.tax()).isEqualByComparingTo("0.00");
        assertThat(order.total()).isEqualByComparingTo("6000.00");
    }

    @Test
    void bulkDeleteOnlyDeletesSelectedStoreOrders() {
        OrderData first = service.create(STORE, OWNER, request(List.of(item("A", "A", "100.00", 1, false, "0")), null, null, null));
        OrderData second = service.create(STORE, OWNER, request(List.of(item("B", "B", "100.00", 1, false, "0")), null, null, null));
        service.create("store-2", OWNER, request(List.of(item("C", "C", "100.00", 1, false, "0")), null, null, null));

        int deleted = service.bulkDelete(STORE, List.of(first.id(), "missing"));

        assertThat(deleted).isEqualTo(1);
        assertThat(service.list(STORE, null, null, null, null, null, null, 1, 0).items())
                .extracting(OrderSummaryData::id)
                .containsExactly(second.id());
        assertThat(service.list("store-2", null, null, null, null, null, null, 1, 0).total()).isEqualTo(1);
    }

    @Test
    void rejectsOrderWithoutProducts() {
        assertThatThrownBy(() -> service.create(STORE, OWNER, request(List.of(), null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("product");
    }

    private static OrderRequest request(
            List<OrderLineItemRequest> items,
            OrderDiscountRequest discount,
            BigDecimal shippingCharge,
            BigDecimal packageCharge) {
        return new OrderRequest(
                null,
                null,
                null,
                "Rahul Sharma",
                "rahul@example.com",
                "+919999999999",
                "paid",
                "pending",
                "Online Store",
                null,
                null,
                "Ship carefully",
                new OrderAddressRequest("A-10", null, "Mumbai", "Maharashtra", "400001", "IN"),
                discount,
                shippingCharge,
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
        return new OrderLineItemRequest(
                null,
                name,
                sku,
                null,
                quantity,
                new BigDecimal(price),
                taxable,
                new BigDecimal(taxRate),
                null);
    }
}
