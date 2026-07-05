package com.ecommerce.order;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Builds human-friendly order numbers from {@link OrderSettings}. Kept as a small static
 * helper so order creation (which allocates the running value) and the settings screen
 * (which only previews the next value) format numbers identically.
 */
final class OrderNumberFormatter {

    /** Financial-year boundaries follow the store's local calendar; this app is India-first. */
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private OrderNumberFormatter() {}

    /**
     * The period a number belongs to. When financial-year reset is on this is the FY label
     * (so the counter restarts each year); otherwise a single {@code "ALL"} bucket keeps a
     * continuous count.
     */
    static String periodKey(OrderSettings settings, Instant at) {
        if (!settings.isFinancialYearReset()) {
            return "ALL";
        }
        return financialYearLabel(settings, at);
    }

    /**
     * Financial-year label such as {@code "2526"} for the year starting April 2025, derived
     * from the configured start month (defaults to April).
     */
    static String financialYearLabel(OrderSettings settings, Instant at) {
        int startMonth = normalizeStartMonth(settings.getFinancialYearStartMonth());
        LocalDate date = LocalDate.ofInstant(at, ZONE);
        int startYear = date.getMonthValue() >= startMonth ? date.getYear() : date.getYear() - 1;
        int endYear = startYear + 1;
        return String.format("%02d%02d", startYear % 100, endYear % 100);
    }

    /** Formats {@code prefix[-fyLabel]-paddedSequence}, e.g. {@code ORD-2526-00001}. */
    static String format(OrderSettings settings, Instant at, long sequenceValue) {
        String prefix = settings.getOrderPrefix() == null || settings.getOrderPrefix().isBlank()
                ? "ORD"
                : settings.getOrderPrefix().trim();
        int padding = settings.getOrderNumberPadding() == null || settings.getOrderNumberPadding() < 1
                ? 5
                : settings.getOrderNumberPadding();
        StringBuilder number = new StringBuilder(prefix);
        if (settings.isIncludeFinancialYear()) {
            number.append('-').append(financialYearLabel(settings, at));
        }
        number.append('-').append(String.format("%0" + padding + "d", sequenceValue));
        return number.toString();
    }

    private static int normalizeStartMonth(Integer month) {
        if (month == null || month < 1 || month > 12) {
            return 4;
        }
        return month;
    }
}
