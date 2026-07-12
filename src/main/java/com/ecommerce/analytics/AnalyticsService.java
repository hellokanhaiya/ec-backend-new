package com.ecommerce.analytics;

import com.ecommerce.abandoned.AbandonedCartRepository;
import com.ecommerce.customer.Customer;
import com.ecommerce.customer.CustomerRepository;
import com.ecommerce.order.FulfillmentStatus;
import com.ecommerce.order.OrderStatus;
import com.ecommerce.order.PaymentStatus;
import com.ecommerce.order.StoreOrder;
import com.ecommerce.order.StoreOrderLineItem;
import com.ecommerce.order.StoreOrderRepository;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final StoreOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final AbandonedCartRepository abandonedCartRepository;

    public AnalyticsService(
            StoreOrderRepository orderRepository,
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            AbandonedCartRepository abandonedCartRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.abandonedCartRepository = abandonedCartRepository;
    }

    // ─── Dashboard Individual Endpoints ────────────────────────────────────────

    public List<DashboardStat> dashboardStats(String storeId) {
        List<StoreOrder> confirmedOrders = getConfirmedOrders(storeId);
        Instant now = Instant.now();
        LocalDate today = now.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate currentMonthStart = today.withDayOfMonth(1);
        LocalDate previousMonthStart = currentMonthStart.minusMonths(1);
        LocalDate previousMonthEnd = currentMonthStart.minusDays(1);

        Instant currentMonthFrom = currentMonthStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant previousMonthFrom = previousMonthStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant previousMonthTo = previousMonthEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        long currentMonthOrders = confirmedOrders.stream().filter(o -> o.getCreatedAt().isAfter(currentMonthFrom)).count();
        BigDecimal currentMonthRevenue = confirmedOrders.stream()
                .filter(o -> o.getCreatedAt().isAfter(currentMonthFrom))
                .map(StoreOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        long previousMonthOrders = confirmedOrders.stream()
                .filter(o -> o.getCreatedAt().isAfter(previousMonthFrom) && o.getCreatedAt().isBefore(previousMonthTo)).count();
        BigDecimal previousMonthRevenue = confirmedOrders.stream()
                .filter(o -> o.getCreatedAt().isAfter(previousMonthFrom) && o.getCreatedAt().isBefore(previousMonthTo))
                .map(StoreOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        String revenueChange = computePercentChange(currentMonthRevenue, previousMonthRevenue);
        String revenueDir = currentMonthRevenue.compareTo(previousMonthRevenue) >= 0 ? "up" : "down";
        String ordersChange = computePercentChange(currentMonthOrders, previousMonthOrders);
        String ordersDir = currentMonthOrders >= previousMonthOrders ? "up" : "down";

        BigDecimal currentAov = currentMonthOrders > 0
                ? currentMonthRevenue.divide(BigDecimal.valueOf(currentMonthOrders), 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal previousAov = previousMonthOrders > 0
                ? previousMonthRevenue.divide(BigDecimal.valueOf(previousMonthOrders), 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        String aovChange = computePercentChange(currentAov, previousAov);
        String aovDir = currentAov.compareTo(previousAov) >= 0 ? "up" : "down";

        List<Customer> allCustomers = customerRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        long totalCustomers = allCustomers.size();
        long currentMonthCustomers = allCustomers.stream().filter(c -> c.getCreatedAt().isAfter(currentMonthFrom)).count();
        long previousMonthCustomers = allCustomers.stream()
                .filter(c -> c.getCreatedAt().isAfter(previousMonthFrom) && c.getCreatedAt().isBefore(previousMonthTo)).count();
        String customersChange = computePercentChange(currentMonthCustomers, previousMonthCustomers);
        String customersDir = currentMonthCustomers >= previousMonthCustomers ? "up" : "down";

        Map<String, Long> customerOrderCounts = confirmedOrders.stream()
                .filter(o -> o.getCustomerPublicId() != null)
                .collect(Collectors.groupingBy(StoreOrder::getCustomerPublicId, Collectors.counting()));
        long repeatBuyers = customerOrderCounts.values().stream().filter(c -> c > 1).count();
        double conversionRate = totalCustomers > 0 ? Math.min(100, (double) currentMonthOrders / totalCustomers * 100) : 0;

        return List.of(
                new DashboardStat("Total Revenue", fmtInr(currentMonthRevenue), revenueChange + "%", "vs last month", revenueDir, "bg-indigo-50 text-indigo-600 dark:bg-indigo-900/30"),
                new DashboardStat("Orders", String.valueOf(currentMonthOrders), ordersChange + "%", "vs last month", ordersDir, "bg-violet-50 text-violet-600 dark:bg-violet-900/30"),
                new DashboardStat("Customers", String.valueOf(totalCustomers), customersChange + "%", "vs last month", customersDir, "bg-emerald-50 text-emerald-600 dark:bg-emerald-900/30"),
                new DashboardStat("Conversion Rate", String.format("%.2f%%", conversionRate), "0%", "vs last month", "up", "bg-amber-50 text-amber-600 dark:bg-amber-900/30"),
                new DashboardStat("Avg. Order Value", fmtInr(currentAov), aovChange + "%", "vs last month", aovDir, "bg-blue-50 text-blue-600 dark:bg-blue-900/30"),
                new DashboardStat("Repeat Buyers", String.valueOf(repeatBuyers), "0%", "vs last month", "up", "bg-rose-50 text-rose-600 dark:bg-rose-900/30"));
    }

    public Map<String, Object> dashboardRevenueTrend(String storeId) {
        List<StoreOrder> confirmedOrders = getConfirmedOrders(storeId);
        LocalDate today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate();
        List<RevenueTrendItem> data = buildMonthlyRevenueTrend(confirmedOrders, today);
        long totalRevenue = confirmedOrders.stream().map(StoreOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add).longValue();
        return Map.of("revenueData", data, "totalRevenue", totalRevenue);
    }

    public List<TrafficSourceItem> dashboardTraffic(String storeId) {
        return buildTrafficData(getConfirmedOrders(storeId));
    }

    public List<RecentOrderData> dashboardRecentOrders(String storeId) {
        List<StoreOrder> allOrders = orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        return allOrders.stream().limit(8).map(o -> new RecentOrderData(
                o.getOrderNumber() != null ? o.getOrderNumber() : o.getPublicOrderId().substring(0, 8),
                o.getCustomerName(),
                o.getLineItems() != null ? o.getLineItems().stream().mapToInt(StoreOrderLineItem::getQuantity).sum() : 0,
                o.getTotal().longValue(),
                o.getPaymentStatus() == PaymentStatus.PAID ? "paid" : "pending",
                mapFulfillment(o.getFulfillmentStatus()),
                formatRelativeTime(o.getCreatedAt()),
                o.getChannel() != null ? o.getChannel() : "Online Store")).toList();
    }

    public List<TopProductData> dashboardTopProducts(String storeId) {
        List<Product> allProducts = productRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        return allProducts.stream()
                .sorted(Comparator.comparingInt((Product p) -> p.getSalesCount() != null ? p.getSalesCount() : 0).reversed())
                .limit(8)
                .map(p -> new TopProductData(
                        p.getTitle(),
                        p.getSalesCount() != null ? p.getSalesCount() : 0,
                        p.getPrice().longValue() * (p.getSalesCount() != null ? p.getSalesCount() : 0),
                        p.getStock() != null ? p.getStock() : 0,
                        p.getCategory() != null ? p.getCategory() : ""))
                .toList();
    }

    public List<LowStockProduct> dashboardLowStock(String storeId) {
        List<Product> allProducts = productRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        return allProducts.stream()
                .filter(p -> p.getStock() != null && p.getStock() <= 10 && p.getStock() >= 0)
                .sorted(Comparator.comparingInt(Product::getStock))
                .limit(10)
                .map(p -> new LowStockProduct(p.getTitle(), p.getStock(), p.getSku()))
                .toList();
    }

    // ─── Analytics Individual Endpoints ────────────────────────────────────────

    public List<KpiData> analyticsKpis(String storeId, String dateRange) {
        DateRange range = resolveDateRange(dateRange);
        List<StoreOrder> confirmedOrders = getConfirmedOrders(storeId);
        List<StoreOrder> currentOrders = filterCurrent(confirmedOrders, range.currentFrom());
        List<StoreOrder> previousOrders = filterPrevious(confirmedOrders, range.previousFrom(), range.previousTo());

        List<Customer> allCustomers = customerRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        long currentCustomers = allCustomers.stream().filter(c -> !c.getCreatedAt().isBefore(range.currentFrom())).count();
        long previousCustomers = allCustomers.stream()
                .filter(c -> !c.getCreatedAt().isBefore(range.previousFrom()) && c.getCreatedAt().isBefore(range.previousTo())).count();

        long currentAbandoned = abandonedCartRepository.findByStoreIdOrderByLastActivityAtDesc(storeId).stream()
                .filter(c -> !c.getCreatedAt().isBefore(range.currentFrom())).count();
        long previousAbandoned = abandonedCartRepository.findByStoreIdOrderByLastActivityAtDesc(storeId).stream()
                .filter(c -> !c.getCreatedAt().isBefore(range.previousFrom()) && c.getCreatedAt().isBefore(range.previousTo())).count();

        BigDecimal currentRevenue = currentOrders.stream().map(StoreOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousRevenue = previousOrders.stream().map(StoreOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal currentAov = currentOrders.isEmpty() ? BigDecimal.ZERO
                : currentRevenue.divide(BigDecimal.valueOf(currentOrders.size()), 0, RoundingMode.HALF_UP);
        BigDecimal previousAov = previousOrders.isEmpty() ? BigDecimal.ZERO
                : previousRevenue.divide(BigDecimal.valueOf(previousOrders.size()), 0, RoundingMode.HALF_UP);

        return List.of(
                buildKpi(String.valueOf(currentCustomers), computePercentChange(currentCustomers, previousCustomers), range.compareText()),
                buildKpi(String.valueOf(currentOrders.size()), computePercentChange(currentOrders.size(), previousOrders.size()), range.compareText()),
                buildKpi(fmtInr(currentAov), computePercentChange(currentAov, previousAov), range.compareText()),
                buildKpi(String.valueOf(currentAbandoned), computePercentChange(currentAbandoned, previousAbandoned), range.compareText()));
    }

    public List<RevenueTrendItem> analyticsRevenueTrend(String storeId, String dateRange) {
        DateRange range = resolveDateRange(dateRange);
        List<StoreOrder> currentOrders = filterCurrent(getConfirmedOrders(storeId), range.currentFrom());
        LocalDate today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate();
        return buildRevenueTrend(currentOrders, range.granularity(), today);
    }

    public List<SalesBreakdownItem> analyticsSalesBreakdown(String storeId, String dateRange) {
        DateRange range = resolveDateRange(dateRange);
        List<StoreOrder> currentOrders = filterCurrent(getConfirmedOrders(storeId), range.currentFrom());
        return buildSalesBreakdown(currentOrders);
    }

    public List<TrafficSourceItem> analyticsTraffic(String storeId, String dateRange) {
        DateRange range = resolveDateRange(dateRange);
        List<StoreOrder> currentOrders = filterCurrent(getConfirmedOrders(storeId), range.currentFrom());
        return buildTrafficData(currentOrders);
    }

    // ─── Shared Helpers ────────────────────────────────────────────────────────

    private List<StoreOrder> getConfirmedOrders(String storeId) {
        return orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .filter(o -> o.getStatus() != OrderStatus.DRAFT).toList();
    }

    private List<StoreOrder> filterCurrent(List<StoreOrder> orders, Instant from) {
        return orders.stream().filter(o -> !o.getCreatedAt().isBefore(from)).toList();
    }

    private List<StoreOrder> filterPrevious(List<StoreOrder> orders, Instant from, Instant to) {
        return orders.stream().filter(o -> !o.getCreatedAt().isBefore(from) && o.getCreatedAt().isBefore(to)).toList();
    }

    private record DateRange(Instant currentFrom, Instant previousFrom, Instant previousTo, String compareText, String granularity) {}

    private DateRange resolveDateRange(String dateRange) {
        Instant now = Instant.now();
        LocalDate today = now.atZone(ZoneId.systemDefault()).toLocalDate();
        ZoneId zone = ZoneId.systemDefault();

        return switch (dateRange != null ? dateRange : "thisMonth") {
            case "today" -> new DateRange(
                    today.atStartOfDay(zone).toInstant(),
                    today.minusDays(1).atStartOfDay(zone).toInstant(),
                    today.atStartOfDay(zone).toInstant(),
                    "vs yesterday", "hourly");
            case "yesterday" -> new DateRange(
                    today.minusDays(1).atStartOfDay(zone).toInstant(),
                    today.minusDays(2).atStartOfDay(zone).toInstant(),
                    today.minusDays(1).atStartOfDay(zone).toInstant(),
                    "vs previous day", "hourly");
            case "thisWeek" -> new DateRange(
                    today.with(java.time.DayOfWeek.MONDAY).atStartOfDay(zone).toInstant(),
                    today.with(java.time.DayOfWeek.MONDAY).minusWeeks(1).atStartOfDay(zone).toInstant(),
                    today.with(java.time.DayOfWeek.MONDAY).atStartOfDay(zone).toInstant(),
                    "vs last week", "daily");
            case "lastWeek" -> new DateRange(
                    today.with(java.time.DayOfWeek.MONDAY).minusWeeks(1).atStartOfDay(zone).toInstant(),
                    today.with(java.time.DayOfWeek.MONDAY).minusWeeks(2).atStartOfDay(zone).toInstant(),
                    today.with(java.time.DayOfWeek.MONDAY).minusWeeks(1).atStartOfDay(zone).toInstant(),
                    "vs previous week", "daily");
            case "lastMonth" -> {
                LocalDate lastMonthStart = today.withDayOfMonth(1).minusMonths(1);
                yield new DateRange(
                        lastMonthStart.atStartOfDay(zone).toInstant(),
                        lastMonthStart.minusMonths(1).atStartOfDay(zone).toInstant(),
                        lastMonthStart.atStartOfDay(zone).toInstant(),
                        "vs previous month", "weekly");
            }
            default -> { // thisMonth
                LocalDate monthStart = today.withDayOfMonth(1);
                yield new DateRange(
                        monthStart.atStartOfDay(zone).toInstant(),
                        monthStart.minusMonths(1).atStartOfDay(zone).toInstant(),
                        monthStart.atStartOfDay(zone).toInstant(),
                        "vs last month", "weekly");
            }
        };
    }

    private KpiData buildKpi(String value, String change, String compareText) {
        return new KpiData(value, change, compareText);
    }

    private List<RevenueTrendItem> buildRevenueTrend(List<StoreOrder> orders, String granularity, LocalDate today) {
        if ("hourly".equals(granularity)) return buildHourlyTrend(orders, today);
        if ("daily".equals(granularity)) return buildDailyTrend(orders, today);
        return buildWeeklyTrend(orders, today);
    }

    private List<RevenueTrendItem> buildHourlyTrend(List<StoreOrder> orders, LocalDate today) {
        String[] labels = {"8 AM", "10 AM", "12 PM", "2 PM", "4 PM", "6 PM"};
        int[] hours = {8, 10, 12, 14, 16, 18};
        List<RevenueTrendItem> result = new ArrayList<>();
        ZoneId zone = ZoneId.systemDefault();
        for (int i = 0; i < labels.length; i++) {
            Instant from = today.atStartOfDay(zone).plusHours(hours[i]).toInstant();
            Instant to = today.atStartOfDay(zone).plusHours(i < labels.length - 1 ? hours[i + 1] : 24).toInstant();
            List<StoreOrder> periodOrders = orders.stream().filter(o -> !o.getCreatedAt().isBefore(from) && o.getCreatedAt().isBefore(to)).toList();
            long revenue = periodOrders.stream().map(StoreOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add).longValue();
            result.add(new RevenueTrendItem(labels[i], revenue, periodOrders.size()));
        }
        return result;
    }

    private List<RevenueTrendItem> buildDailyTrend(List<StoreOrder> orders, LocalDate today) {
        String[] dayNames = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
        List<RevenueTrendItem> result = new ArrayList<>();
        ZoneId zone = ZoneId.systemDefault();
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            Instant from = day.atStartOfDay(zone).toInstant();
            Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
            List<StoreOrder> dayOrders = orders.stream().filter(o -> !o.getCreatedAt().isBefore(from) && o.getCreatedAt().isBefore(to)).toList();
            long revenue = dayOrders.stream().map(StoreOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add).longValue();
            result.add(new RevenueTrendItem(dayNames[i], revenue, dayOrders.size()));
        }
        return result;
    }

    private List<RevenueTrendItem> buildWeeklyTrend(List<StoreOrder> orders, LocalDate today) {
        LocalDate monthStart = today.withDayOfMonth(1);
        int numWeeks = (int) Math.ceil(monthStart.lengthOfMonth() / 7.0);
        List<RevenueTrendItem> result = new ArrayList<>();
        ZoneId zone = ZoneId.systemDefault();
        for (int w = 0; w < numWeeks; w++) {
            LocalDate weekStart = monthStart.plusDays(w * 7);
            LocalDate weekEnd = weekStart.plusDays(7);
            if (weekEnd.isAfter(monthStart.plusMonths(1))) weekEnd = monthStart.plusMonths(1);
            Instant from = weekStart.atStartOfDay(zone).toInstant();
            Instant to = weekEnd.atStartOfDay(zone).toInstant();
            List<StoreOrder> weekOrders = orders.stream().filter(o -> !o.getCreatedAt().isBefore(from) && o.getCreatedAt().isBefore(to)).toList();
            long revenue = weekOrders.stream().map(StoreOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add).longValue();
            result.add(new RevenueTrendItem("Week " + (w + 1), revenue, weekOrders.size()));
        }
        return result;
    }

    private List<RevenueTrendItem> buildMonthlyRevenueTrend(List<StoreOrder> orders, LocalDate today) {
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        List<RevenueTrendItem> result = new ArrayList<>();
        ZoneId zone = ZoneId.systemDefault();
        for (int m = 1; m <= 12; m++) {
            LocalDate mStart = LocalDate.of(today.getYear(), m, 1);
            Instant from = mStart.atStartOfDay(zone).toInstant();
            Instant to = mStart.plusMonths(1).atStartOfDay(zone).toInstant();
            List<StoreOrder> monthOrders = orders.stream().filter(o -> !o.getCreatedAt().isBefore(from) && o.getCreatedAt().isBefore(to)).toList();
            long revenue = monthOrders.stream().map(StoreOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add).longValue();
            result.add(new RevenueTrendItem(months[m - 1], revenue, monthOrders.size()));
        }
        return result;
    }

    private List<SalesBreakdownItem> buildSalesBreakdown(List<StoreOrder> orders) {
        BigDecimal grossSales = orders.stream().map(StoreOrder::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discounts = orders.stream().map(StoreOrder::getDiscountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shipping = orders.stream().map(StoreOrder::getShippingCharge).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taxes = orders.stream().map(StoreOrder::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netSales = grossSales.subtract(discounts);
        BigDecimal totalSales = netSales.add(shipping).add(taxes);
        List<SalesBreakdownItem> items = new ArrayList<>();
        items.add(new SalesBreakdownItem("Gross sales", fmtInr(grossSales)));
        items.add(new SalesBreakdownItem("Discounts", "-" + fmtInr(discounts)));
        items.add(new SalesBreakdownItem("Returns", "₹0"));
        items.add(new SalesBreakdownItem("Net sales", fmtInr(netSales)));
        items.add(new SalesBreakdownItem("Shipping charges", fmtInr(shipping)));
        items.add(new SalesBreakdownItem("Return fees", "₹0"));
        items.add(new SalesBreakdownItem("Taxes", fmtInr(taxes)));
        items.add(new SalesBreakdownItem("Total sales", fmtInr(totalSales)));
        return items;
    }

    private List<TrafficSourceItem> buildTrafficData(List<StoreOrder> orders) {
        Map<String, Long> channelCounts = orders.stream()
                .filter(o -> o.getChannel() != null && !o.getChannel().isBlank())
                .collect(Collectors.groupingBy(StoreOrder::getChannel, Collectors.counting()));
        long total = channelCounts.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            return List.of(
                    new TrafficSourceItem("Organic", 40, "#6366f1"),
                    new TrafficSourceItem("Direct", 25, "#8b5cf6"),
                    new TrafficSourceItem("Social", 20, "#10b981"),
                    new TrafficSourceItem("Email", 10, "#f59e0b"),
                    new TrafficSourceItem("Paid", 5, "#ef4444"));
        }
        String[] colors = {"#6366f1", "#8b5cf6", "#10b981", "#f59e0b", "#ef4444"};
        List<Map.Entry<String, Long>> sorted = channelCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(5).toList();
        List<TrafficSourceItem> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            int pct = (int) Math.round(sorted.get(i).getValue() * 100.0 / total);
            result.add(new TrafficSourceItem(sorted.get(i).getKey(), pct, colors[i % colors.length]));
        }
        return result;
    }

    private String mapFulfillment(FulfillmentStatus status) {
        if (status == null) return "pending";
        return switch (status) {
            case FULFILLED, DELIVERED -> "fulfilled";
            case SHIPPED -> "shipped";
            case UNFULFILLED -> "unfulfilled";
            default -> "pending";
        };
    }

    private String formatRelativeTime(Instant instant) {
        if (instant == null) return "";
        long seconds = Instant.now().getEpochSecond() - instant.getEpochSecond();
        if (seconds < 60) return "just now";
        if (seconds < 3600) return (seconds / 60) + " min ago";
        if (seconds < 86400) return (seconds / 3600) + " hr ago";
        return (seconds / 86400) + " days ago";
    }

    private String fmtInr(BigDecimal amount) {
        if (amount == null) return "₹0";
        return "₹" + String.format("%,d", amount.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    private String computePercentChange(long current, long previous) {
        if (previous == 0) return current > 0 ? "+100" : "0";
        double pct = ((double) (current - previous) / previous) * 100;
        return String.format("%s%.0f", pct >= 0 ? "+" : "", pct);
    }

    private String computePercentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return (current != null && current.compareTo(BigDecimal.ZERO) > 0) ? "+100" : "0";
        }
        double pct = current.subtract(previous).divide(previous, 4, RoundingMode.HALF_UP).doubleValue() * 100;
        return String.format("%s%.0f", pct >= 0 ? "+" : "", pct);
    }
}
