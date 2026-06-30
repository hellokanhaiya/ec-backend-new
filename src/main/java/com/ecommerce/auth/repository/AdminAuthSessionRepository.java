package com.ecommerce.auth.repository;

import com.ecommerce.auth.entity.AdminAuthSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuthSessionRepository extends JpaRepository<AdminAuthSession, Long> {
    Optional<AdminAuthSession> findTopByAccessTokenHashAndRevokedAtIsNullOrderByUpdatedAtDesc(String accessTokenHash);
}
