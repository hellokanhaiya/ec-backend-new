package com.ecommerce.shipping;

import java.math.BigDecimal;

/**
 * Simple "most stores" delivery setup for merchants who ship across their whole country and
 * don't need multi-zone rules. Configures the default profile's default zone in one call.
 *
 * <p>{@code mode}:
 * <ul>
 *   <li>{@code FLAT} — one flat {@code charge}; if {@code freeAbove} is set, orders at/above it ship free.</li>
 *   <li>{@code FREE} — always free.</li>
 *   <li>{@code DISTANCE} — {@code charge} base + {@code perKm} per km (distance from the pincode range).</li>
 * </ul>
 * A broad serviceable pincode range is ensured so the whole country is deliverable out of the box.
 */
public record ShippingQuickSetupRequest(
        String mode,
        BigDecimal charge,
        BigDecimal freeAbove,
        BigDecimal perKm,
        Integer etaMinDays,
        Integer etaMaxDays,
        Boolean codAvailable) {}
