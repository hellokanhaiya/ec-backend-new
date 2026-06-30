package com.ecommerce.auth;

import java.time.Instant;

public record CurrentAccountUserData(
        Long userId,
        String publicUserId,
        String displayName,
        String email,
        String phoneNumber,
        String status,
        Instant emailVerifiedAt,
        Instant phoneVerifiedAt) {}
