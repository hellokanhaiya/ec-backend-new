package com.ecommerce.access;

/** Invite payload: assign an email to a role. The member links to an admin user on first sign-in. */
public record MemberInviteRequest(
        String email,
        String displayName,
        String title,
        String rolePublicId) {}
