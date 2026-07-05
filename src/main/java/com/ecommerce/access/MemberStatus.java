package com.ecommerce.access;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Lifecycle of a staff member's association with a store. */
public enum MemberStatus {
    /** Linked to a signed-in admin user and allowed in. */
    ACTIVE,
    /** Invited by email; not yet linked to an admin user (fills in on first OTP sign-in). */
    INVITED,
    /** Temporarily suspended; keeps the row but grants no access. */
    PAUSED;

    /** Serialize as lowercase on the wire (matches the frontend {@code active|invited|paused}). */
    @JsonValue
    public String wireValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    @JsonCreator
    public static MemberStatus from(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        try {
            return MemberStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ACTIVE;
        }
    }
}
