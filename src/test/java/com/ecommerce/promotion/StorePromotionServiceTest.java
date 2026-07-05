package com.ecommerce.promotion;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.order.StoreOrderRepository;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.store.StoreProfileRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(
        properties = {
            "spring.config.import=",
            "spring.datasource.url=jdbc:h2:mem:promotions;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password="
        })
class StorePromotionServiceTest {
    private static final String STORE = "store-1";
    private static final String OWNER = "owner-1";

    @Autowired private StorePromotionRepository promotionRepository;
    @Autowired private StoreOrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;

    private StorePromotionService service;

    @BeforeEach
    void setUp() {
        service = new StorePromotionService(promotionRepository, orderRepository, productRepository);
    }

    @Test
    void resolvesBuyXGetYPromotionForSpecificProduct() {
        Product product = productRepository.save(product("veg-chili", "Chili", "cat-veg", "120.00"));

        PromotionRequest request = new PromotionRequest(
                "Veggie BOGO",
                "VEGBOGO",
                "code",
                "buy-x-get-y",
                "fixed",
                BigDecimal.ZERO,
                "all",
                null,
                List.of(),
                "none",
                null,
                null,
                "unlimited",
                null,
                null,
                true,
                true,
                true,
                List.of("Seasonal"),
                null,
                null,
                "products",
                List.of(product.getPublicProductId()),
                "products",
                List.of(product.getPublicProductId()),
                "products",
                List.of(product.getPublicProductId()),
                2,
                1,
                "free",
                BigDecimal.ZERO,
                "active");

        PromotionData saved = service.create(STORE, OWNER, request);
        PromotionApplyData resolved = service.resolve(
                STORE,
                new PromotionApplyRequest(
                        "VEGBOGO",
                        null,
                        new BigDecimal("50.00"),
                        List.of(
                                new PromotionItemRequest(product.getPublicProductId(), 3, new BigDecimal("120.00")))));

        assertThat(saved.code()).isEqualTo("VEGBOGO");
        assertThat(resolved.found()).isTrue();
        assertThat(resolved.qualifies()).isTrue();
        assertThat(resolved.discountAmount()).isEqualByComparingTo("120.00");
        assertThat(resolved.finalShippingCharge()).isEqualByComparingTo("50.00");
        assertThat(resolved.finalTotal()).isEqualByComparingTo("290.00");
    }

    private Product product(String sku, String title, String categoryPublicId, String price) {
        Product product = new Product();
        product.setStoreId(STORE);
        product.setOwnerPublicUserId(OWNER);
        product.setSku(sku);
        product.setTitle(title);
        product.setCategoryPublicId(categoryPublicId);
        product.setCategory("Vegetables");
        product.setPrice(new BigDecimal(price));
        product.setTrackInventory(false);
        product.setStatus(com.ecommerce.product.ProductStatus.ACTIVE);
        product.setProductType(com.ecommerce.product.ProductType.PHYSICAL);
        product.setRequiresShipping(true);
        product.setTaxable(true);
        return product;
    }
}
