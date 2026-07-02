package com.ecommerce.customer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByStoreIdOrderByCreatedAtDesc(String storeId);

    Optional<Customer> findByStoreIdAndPublicCustomerId(String storeId, String publicCustomerId);
}
