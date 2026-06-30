package com.ecommerce.auth.repository;

import com.ecommerce.auth.entity.ConsumerAuthUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumerAuthUserRepository extends JpaRepository<ConsumerAuthUser, Long> {
    Optional<ConsumerAuthUser> findByEmailIgnoreCase(String email);

    Optional<ConsumerAuthUser> findByPhoneNumber(String phoneNumber);

    Optional<ConsumerAuthUser> findByPublicId(String publicId);
}
