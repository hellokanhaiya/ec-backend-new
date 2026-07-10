package com.ecommerce.customer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByStoreIdOrderByCreatedAtDesc(String storeId);

    Optional<Customer> findByStoreIdAndPublicCustomerId(String storeId, String publicCustomerId);

    /** Duplicate-contact checks are scoped to the store (email is case-insensitive). */
    Optional<Customer> findByStoreIdAndEmailIgnoreCase(String storeId, String email);

    List<Customer> findByStoreIdAndPhone(String storeId, String phone);
}
