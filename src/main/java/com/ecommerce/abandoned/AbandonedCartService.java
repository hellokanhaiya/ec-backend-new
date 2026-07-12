package com.ecommerce.abandoned;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read + light-write operations over {@link AbandonedCart}, scoped to a store.
 *
 * <p>Because this is a demo/learning project with no production checkout pipeline feeding the
 * table, the first time a store views its abandoned carts and has none, a realistic set of demo
 * rows is persisted for that store (idempotent: only when the store is empty). This mirrors the
 * "seed defaults on first touch" pattern used by StoreAccessService.
 */
@Service
@Transactional
public class AbandonedCartService {

    private final AbandonedCartRepository repository;

    public AbandonedCartService(AbandonedCartRepository repository) {
        this.repository = repository;
    }

    // --- Queries ------------------------------------------------------------

    @Transactional
    public AbandonedCartListData list(
            String storeId,
            String ownerPublicUserId,
            String search,
            String status,
            int page,
            int size) {
        ensureDemoData(storeId, ownerPublicUserId);

        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        RecoveryStatus statusFilter =
                (status == null || status.isBlank() || "all".equalsIgnoreCase(status))
                        ? null
                        : RecoveryStatus.from(status);

        List<AbandonedCart> filtered =
                repository.findByStoreIdOrderByLastActivityAtDesc(storeId).stream()
                        .filter(cart -> statusFilter == null || cart.getRecoveryStatus() == statusFilter)
                        .filter(cart -> matchesSearch(cart, query))
                        .toList();

        long total = filtered.size();
        List<AbandonedCart> pageItems;
        if (size <= 0) {
            pageItems = filtered;
        } else {
            int from = Math.max(0, (Math.max(page, 1) - 1) * size);
            int to = Math.min(filtered.size(), from + size);
            pageItems = from >= filtered.size() ? List.of() : filtered.subList(from, to);
        }

        return new AbandonedCartListData(
                pageItems.stream().map(this::toData).toList(),
                total,
                Math.max(page, 1),
                size);
    }

    @Transactional
    public AbandonedCartOverviewData overview(String storeId, String ownerPublicUserId) {
        ensureDemoData(storeId, ownerPublicUserId);
        List<AbandonedCart> carts = repository.findByStoreIdOrderByLastActivityAtDesc(storeId);

        long total = carts.size();
        long active = carts.stream().filter(c -> c.getRecoveryStatus() == RecoveryStatus.ACTIVE).count();
        long recovered = carts.stream().filter(c -> c.getRecoveryStatus() == RecoveryStatus.RECOVERED).count();
        long lost = carts.stream().filter(c -> c.getRecoveryStatus() == RecoveryStatus.LOST).count();

        BigDecimal totalValue =
                carts.stream().map(AbandonedCart::getCartValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal recoverableValue =
                carts.stream()
                        .filter(c -> c.getRecoveryStatus() == RecoveryStatus.ACTIVE)
                        .map(AbandonedCart::getCartValue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        double recoveryRate =
                total == 0 ? 0d : BigDecimal.valueOf(recovered * 100.0 / total)
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();

        return new AbandonedCartOverviewData(
                total, active, recovered, lost, totalValue, recoverableValue, recoveryRate);
    }

    @Transactional
    public AbandonedCartData get(String storeId, String publicCartId) {
        return toData(require(storeId, publicCartId));
    }

    // --- Mutations ----------------------------------------------------------

    /** Records that a recovery reminder was sent to the shopper. */
    @Transactional
    public AbandonedCartData sendRecoveryEmail(String storeId, String publicCartId) {
        AbandonedCart cart = require(storeId, publicCartId);
        if (cart.getRecoveryStatus() == RecoveryStatus.LOST) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This cart is marked as lost and cannot be recovered.");
        }
        cart.setRecoveryEmailsSent(cart.getRecoveryEmailsSent() + 1);
        cart.setLastActivityAt(Instant.now());
        return toData(repository.save(cart));
    }

    // --- Helpers ------------------------------------------------------------

    private AbandonedCart require(String storeId, String publicCartId) {
        return repository
                .findByStoreIdAndPublicCartId(storeId, publicCartId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Abandoned cart not found"));
    }

    private boolean matchesSearch(AbandonedCart cart, String query) {
        if (query.isEmpty()) {
            return true;
        }
        StringBuilder haystack = new StringBuilder();
        haystack.append(safe(cart.getCustomerName()))
                .append(' ').append(safe(cart.getEmail()))
                .append(' ').append(safe(cart.getPhone()))
                .append(' ').append(safe(cart.getChannel()));
        if (!cart.getLineItems().isEmpty()) {
            haystack.append(' ').append(safe(cart.getLineItems().get(0).getName()));
        }
        return haystack.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private AbandonedCartData toData(AbandonedCart cart) {
        List<AbandonedCartItemData> products =
                cart.getLineItems().stream()
                        .map(
                                item ->
                                        new AbandonedCartItemData(
                                                item.getProductPublicId(),
                                                item.getName(),
                                                item.getVariant(),
                                                item.getImageUrl(),
                                                item.getQuantity(),
                                                item.getPrice()))
                        .toList();
        return new AbandonedCartData(
                cart.getPublicCartId(),
                cart.getCustomerName(),
                cart.getEmail(),
                cart.getPhone(),
                cart.getChannel(),
                cart.getRecoveryStatus().apiValue(),
                cart.getRecoveryEmailsSent(),
                cart.getCheckoutUrl(),
                cart.totalItems(),
                cart.getSubtotal(),
                cart.getCartValue(),
                cart.getCurrencyCode(),
                cart.getCurrencySymbol(),
                products,
                cart.getLastActivityAt(),
                cart.getCreatedAt(),
                cart.getUpdatedAt());
    }

    // --- Demo data seeding --------------------------------------------------

    /** Idempotently inserts realistic demo carts the first time a store has none. */
    private void ensureDemoData(String storeId, String ownerPublicUserId) {
        if (storeId == null || storeId.isBlank()) {
            return;
        }
        if (repository.countByStoreId(storeId) > 0) {
            return;
        }
        List<AbandonedCart> seeds = DemoAbandonedCarts.build(storeId, ownerPublicUserId);
        repository.saveAll(seeds);
    }

    /** Builders for the demo dataset, kept separate so the seed content is easy to tweak. */
    private static final class DemoAbandonedCarts {
        private DemoAbandonedCarts() {}

        static List<AbandonedCart> build(String storeId, String ownerPublicUserId) {
            Instant now = Instant.now();
            List<AbandonedCart> carts = new ArrayList<>();

            carts.add(
                    cart(storeId, ownerPublicUserId, "Aarav Sharma", "aarav.sharma@gmail.com",
                            "+91 98200 11223", RecoveryStatus.ACTIVE, 0, hoursAgo(now, 3),
                            List.of(
                                    item("Wireless Noise-Cancelling Headphones", "Black", 1, "7999.00",
                                            "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=200"),
                                    item("USB-C Fast Charger 65W", null, 1, "1499.00",
                                            "https://images.unsplash.com/photo-1583863788434-e58a36330cf0?w=200"))));

            carts.add(
                    cart(storeId, ownerPublicUserId, "Priya Nair", "priya.nair@outlook.com",
                            "+91 99860 44521", RecoveryStatus.ACTIVE, 1, hoursAgo(now, 9),
                            List.of(
                                    item("Cotton Kurti Set", "Size M / Teal", 2, "1299.00",
                                            "https://images.unsplash.com/photo-1583391733956-6c78276477e2?w=200"))));

            carts.add(
                    cart(storeId, ownerPublicUserId, "Rohan Verma", "rohan.verma@yahoo.com",
                            "+91 90045 78120", RecoveryStatus.RECOVERED, 2, daysAgo(now, 1),
                            List.of(
                                    item("Smart Fitness Band", "Midnight", 1, "3499.00",
                                            "https://images.unsplash.com/photo-1575311373937-040b8e1fd5b6?w=200"),
                                    item("Silicone Replacement Strap", "Blue", 1, "499.00", null))));

            carts.add(
                    cart(storeId, ownerPublicUserId, "Sneha Iyer", "sneha.iyer@gmail.com",
                            "+91 98765 33210", RecoveryStatus.ACTIVE, 0, hoursAgo(now, 20),
                            List.of(
                                    item("Ceramic Coffee Mug (Set of 4)", "Pastel", 1, "899.00",
                                            "https://images.unsplash.com/photo-1514228742587-6b1558fcca3d?w=200"),
                                    item("French Press 600ml", null, 1, "1199.00", null))));

            carts.add(
                    cart(storeId, ownerPublicUserId, "Kabir Singh", "kabir.singh@hotmail.com",
                            "+91 97400 88990", RecoveryStatus.LOST, 3, daysAgo(now, 6),
                            List.of(
                                    item("Running Shoes", "UK 9 / Grey", 1, "4599.00",
                                            "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=200"))));

            carts.add(
                    cart(storeId, ownerPublicUserId, "Ananya Reddy", "ananya.reddy@gmail.com",
                            "+91 96300 12045", RecoveryStatus.ACTIVE, 1, hoursAgo(now, 30),
                            List.of(
                                    item("Bluetooth Speaker", "Charcoal", 1, "2799.00",
                                            "https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?w=200"),
                                    item("AUX Cable 1.5m", null, 2, "199.00", null))));

            carts.add(
                    cart(storeId, ownerPublicUserId, "Vikram Menon", "vikram.menon@gmail.com",
                            "+91 90210 65432", RecoveryStatus.RECOVERED, 1, daysAgo(now, 2),
                            List.of(
                                    item("Leather Wallet", "Tan", 1, "1899.00",
                                            "https://images.unsplash.com/photo-1627123424574-724758594e93?w=200"))));

            carts.add(
                    cart(storeId, ownerPublicUserId, "Meera Joshi", "meera.joshi@yahoo.in",
                            "+91 98111 22334", RecoveryStatus.ACTIVE, 0, daysAgo(now, 3),
                            List.of(
                                    item("Yoga Mat 6mm", "Purple", 1, "1099.00",
                                            "https://images.unsplash.com/photo-1591291621164-2c6367723315?w=200"),
                                    item("Steel Water Bottle 1L", "Matte Black", 1, "749.00", null),
                                    item("Resistance Band Set", null, 1, "899.00", null))));

            carts.add(
                    cart(storeId, ownerPublicUserId, "Aditya Kulkarni", "aditya.k@gmail.com",
                            "+91 99999 45671", RecoveryStatus.ACTIVE, 2, daysAgo(now, 4),
                            List.of(
                                    item("Mechanical Keyboard", "Brown Switch", 1, "5499.00",
                                            "https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=200"))));

            carts.add(
                    cart(storeId, ownerPublicUserId, "Isha Gupta", "isha.gupta@outlook.com",
                            "+91 98330 90876", RecoveryStatus.LOST, 4, daysAgo(now, 11),
                            List.of(
                                    item("Skincare Gift Box", "Festive", 1, "2299.00",
                                            "https://images.unsplash.com/photo-1556228453-efd6c1ff04f6?w=200"),
                                    item("Facial Roller", "Rose Quartz", 1, "699.00", null))));

            return carts;
        }

        private static AbandonedCart cart(
                String storeId,
                String ownerPublicUserId,
                String name,
                String email,
                String phone,
                RecoveryStatus status,
                int emailsSent,
                Instant lastActivity,
                List<AbandonedCartItem> items) {
            AbandonedCart cart = new AbandonedCart();
            cart.setStoreId(storeId);
            cart.setOwnerPublicUserId(ownerPublicUserId == null ? storeId : ownerPublicUserId);
            cart.setCustomerName(name);
            cart.setEmail(email);
            cart.setPhone(phone);
            cart.setChannel("Online Store");
            cart.setRecoveryStatus(status);
            cart.setRecoveryEmailsSent(emailsSent);
            cart.setCurrencyCode("INR");
            cart.setCurrencySymbol("₹");
            cart.setLastActivityAt(lastActivity);

            BigDecimal subtotal = BigDecimal.ZERO;
            for (AbandonedCartItem item : items) {
                subtotal = subtotal.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                cart.getLineItems().add(item);
            }
            cart.setSubtotal(subtotal);
            cart.setCartValue(subtotal);
            cart.setCheckoutUrl("https://shop.example.com/checkout/resume?cart=demo");
            return cart;
        }

        private static AbandonedCartItem item(
                String name, String variant, int quantity, String price, String imageUrl) {
            AbandonedCartItem item = new AbandonedCartItem();
            item.setName(name);
            item.setVariant(variant);
            item.setQuantity(quantity);
            item.setPrice(new BigDecimal(price));
            item.setImageUrl(imageUrl);
            return item;
        }

        private static Instant hoursAgo(Instant now, int hours) {
            return now.minus(hours, ChronoUnit.HOURS);
        }

        private static Instant daysAgo(Instant now, int days) {
            return now.minus(days, ChronoUnit.DAYS);
        }
    }
}
