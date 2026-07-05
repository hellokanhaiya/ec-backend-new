package com.ecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OrderNumberFormatterTest {

    private static Instant on(int year, int month, int day) {
        return LocalDate.of(year, month, day).atStartOfDay(OrderNumberFormatter.ZONE).toInstant();
    }

    private static OrderSettings settings() {
        OrderSettings s = new OrderSettings();
        s.setStoreId("store-1");
        s.setOrderPrefix("ORD");
        s.setOrderNumberPadding(5);
        s.setFinancialYearReset(true);
        s.setIncludeFinancialYear(true);
        s.setFinancialYearStartMonth(4);
        return s;
    }

    @Test
    void financialYearLabelFollowsAprilBoundary() {
        OrderSettings s = settings();
        // May 2025 and Feb 2026 both fall in FY 2025-26.
        assertThat(OrderNumberFormatter.financialYearLabel(s, on(2025, 5, 10))).isEqualTo("2526");
        assertThat(OrderNumberFormatter.financialYearLabel(s, on(2026, 2, 15))).isEqualTo("2526");
        // April 1 rolls into the next FY.
        assertThat(OrderNumberFormatter.financialYearLabel(s, on(2026, 4, 1))).isEqualTo("2627");
    }

    @Test
    void respectsConfiguredStartMonth() {
        OrderSettings s = settings();
        s.setFinancialYearStartMonth(1); // calendar-year businesses
        assertThat(OrderNumberFormatter.financialYearLabel(s, on(2026, 2, 15))).isEqualTo("2627");
    }

    @Test
    void formatEmbedsFinancialYearWhenEnabled() {
        OrderSettings s = settings();
        assertThat(OrderNumberFormatter.format(s, on(2025, 5, 10), 1)).isEqualTo("ORD-2526-00001");
        assertThat(OrderNumberFormatter.format(s, on(2025, 5, 10), 42)).isEqualTo("ORD-2526-00042");
    }

    @Test
    void formatOmitsFinancialYearWhenDisabled() {
        OrderSettings s = settings();
        s.setIncludeFinancialYear(false);
        assertThat(OrderNumberFormatter.format(s, on(2025, 5, 10), 7)).isEqualTo("ORD-00007");
    }

    @Test
    void periodKeyIsSharedBucketWhenResetDisabled() {
        OrderSettings s = settings();
        s.setFinancialYearReset(false);
        assertThat(OrderNumberFormatter.periodKey(s, on(2025, 5, 10))).isEqualTo("ALL");
    }

    @Test
    void periodKeyTracksFinancialYearWhenResetEnabled() {
        OrderSettings s = settings();
        assertThat(OrderNumberFormatter.periodKey(s, on(2025, 5, 10))).isEqualTo("2526");
        assertThat(OrderNumberFormatter.periodKey(s, on(2026, 4, 1))).isEqualTo("2627");
    }
}
