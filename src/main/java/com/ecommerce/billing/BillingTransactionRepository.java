package com.ecommerce.billing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingTransactionRepository extends JpaRepository<BillingTransaction, Long> {
    List<BillingTransaction> findByStoreIdOrderByCreatedAtDesc(String storeId);

    Optional<BillingTransaction> findByStoreIdAndRazorpayOrderId(String storeId, String razorpayOrderId);
}
