package com.ecommerce.auth.repository;

import com.ecommerce.auth.entity.AdminAuthUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuthUserRepository extends JpaRepository<AdminAuthUser, Long> {
    Optional<AdminAuthUser> findByEmailIgnoreCase(String email);

    Optional<AdminAuthUser> findByPhoneNumber(String phoneNumber);

    Optional<AdminAuthUser> findByPublicId(String publicId);
}
