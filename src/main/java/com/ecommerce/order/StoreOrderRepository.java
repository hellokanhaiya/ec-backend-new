package com.ecommerce.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long> {
    List<StoreOrder> findByStoreIdOrderByCreatedAtDesc(String storeId);

    Optional<StoreOrder> findByStoreIdAndPublicOrderId(String storeId, String publicOrderId);

    long countByStoreIdAndCustomerPublicIdAndPromotionPublicId(
            String storeId, String customerPublicId, String promotionPublicId);

    /**
     * Per-customer order count and lifetime spend for a store, keyed by the
     * customer's public id. Drafts (and customer-less orders) are excluded so the
     * figures reflect real, placed orders. A null status is treated as CONFIRMED,
     * matching the entity default for legacy rows.
     */
    @Query("SELECT o.customerPublicId AS customerPublicId, COUNT(o) AS orderCount, "
            + "COALESCE(SUM(o.total), 0) AS totalSpent "
            + "FROM StoreOrder o "
            + "WHERE o.storeId = :storeId AND o.customerPublicId IS NOT NULL "
            + "AND (o.status IS NULL OR o.status <> com.ecommerce.order.OrderStatus.DRAFT) "
            + "GROUP BY o.customerPublicId")
    List<CustomerOrderStats> aggregateCustomerOrderStats(@Param("storeId") String storeId);
}
