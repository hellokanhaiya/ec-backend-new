package com.ecommerce.auth.repository;

import com.ecommerce.auth.entity.ConsumerAuthSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumerAuthSessionRepository extends JpaRepository<ConsumerAuthSession, Long> {
    Optional<ConsumerAuthSession> findTopByAccessTokenHashAndRevokedAtIsNullOrderByUpdatedAtDesc(String accessTokenHash);
}
