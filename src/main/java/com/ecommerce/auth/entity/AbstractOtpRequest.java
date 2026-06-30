package com.ecommerce.auth.entity;

import com.ecommerce.auth.AuthChannel;
import com.ecommerce.auth.AuthPurpose;
import com.ecommerce.auth.OtpRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class AbstractOtpRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AuthPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AuthChannel channel;

    @Column(nullable = false, length = 255)
    private String identifier;

    @Column(name = "normalized_identifier", nullable = false, length = 255)
    private String normalizedIdentifier;

    @Column(name = "masked_destination", nullable = false, length = 255)
    private String maskedDestination;

    @Column(name = "otp_hash", nullable = false, length = 128)
    private String otpHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OtpRequestStatus status = OtpRequestStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "request_count", nullable = false)
    private int requestCount = 1;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resend_available_at")
    private Instant resendAvailableAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "delivery_status", length = 24)
    private String deliveryStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
