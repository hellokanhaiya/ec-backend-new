package com.ecommerce.access;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Page-level access granted by a role.
 *
 * <p>The ordering is significant: a higher level always satisfies the requirement of a lower one
 * ({@code NONE < VIEW < EDIT < MANAGE}). Enforcement compares the caller's level against the level
 * an endpoint requires via {@link #satisfies(AccessLevel)}.
 */
public enum AccessLevel {
    /** Hidden from the sidebar/search and blocked from the API. */
    NONE,
    /** Can open and read the page. */
    VIEW,
    /** Can update existing records on the page. */
    EDIT,
    /** Can create, edit, and delete. */
    MANAGE;

    /** True when this level meets or exceeds {@code required}. */
    public boolean satisfies(AccessLevel required) {
        return required == null || this.ordinal() >= required.ordinal();
    }

    /** Serialize as lowercase on the wire (matches the frontend {@code none|view|edit|manage}). */
    @JsonValue
    public String wireValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Lenient parse that falls back to {@link #NONE} for unknown/blank input. */
    @JsonCreator
    public static AccessLevel from(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return AccessLevel.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }
}
