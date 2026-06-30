package com.ecommerce.auth.repository;

import com.ecommerce.auth.AuthChannel;
import com.ecommerce.auth.AuthPurpose;
import com.ecommerce.auth.entity.AdminOtpRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminOtpRequestRepository extends JpaRepository<AdminOtpRequest, Long> {
    Optional<AdminOtpRequest> findTopByNormalizedIdentifierAndPurposeAndChannelOrderByCreatedAtDesc(
            String normalizedIdentifier,
            AuthPurpose purpose,
            AuthChannel channel);
}
