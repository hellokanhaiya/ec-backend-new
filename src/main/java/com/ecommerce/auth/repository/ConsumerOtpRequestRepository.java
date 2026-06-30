package com.ecommerce.auth.repository;

import com.ecommerce.auth.AuthChannel;
import com.ecommerce.auth.AuthPurpose;
import com.ecommerce.auth.entity.ConsumerOtpRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumerOtpRequestRepository extends JpaRepository<ConsumerOtpRequest, Long> {
    Optional<ConsumerOtpRequest> findTopByNormalizedIdentifierAndPurposeAndChannelOrderByCreatedAtDesc(
            String normalizedIdentifier,
            AuthPurpose purpose,
            AuthChannel channel);
}
