package com.ecommerce.access;

/** Update payload for a member: change role, status (active/paused), and/or title. */
public record MemberUpdateRequest(
        String rolePublicId,
        MemberStatus status,
        String title) {}
