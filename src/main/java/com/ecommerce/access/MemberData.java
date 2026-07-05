package com.ecommerce.access;

import java.time.Instant;

/** A store member for the Users tab. */
public record MemberData(
        String publicId,
        String displayName,
        String email,
        String title,
        String rolePublicId,
        String roleKey,
        String roleName,
        MemberStatus status,
        boolean linked,
        Instant invitedAt,
        Instant lastActiveAt) {}
