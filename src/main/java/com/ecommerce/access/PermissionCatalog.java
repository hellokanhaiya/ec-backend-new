package com.ecommerce.access;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The canonical RBAC taxonomy: every page/capability access can be granted on, plus the twelve
 * default role templates seeded into each store. This is the server-side twin of the frontend
 * {@code src/app/config/permissions.ts} — the key set must stay in sync across both sides.
 */
public final class PermissionCatalog {

    // --- Permission keys (kept as constants so controllers reference them symbolically) ---------
    // Orders
    public static final String ORDERS_ALL = "orders.all";
    public static final String ORDERS_CREATE = "orders.create";
    public static final String ORDERS_DRAFTS = "orders.drafts";
    public static final String ORDERS_ABANDONED = "orders.abandoned";
    public static final String ORDERS_RETURNS = "orders.returns";
    public static final String ORDERS_EXCHANGES = "orders.exchanges";
    public static final String ORDERS_PURCHASE = "orders.purchase";
    public static final String ORDERS_BULK = "orders.bulk";
    public static final String ORDERS_EXPORT = "orders.export";

    // Products
    public static final String PRODUCTS_LIST = "products.list";
    public static final String PRODUCTS_CREATE = "products.create";
    public static final String PRODUCTS_CATEGORIES = "products.categories";
    public static final String PRODUCTS_COLLECTIONS = "products.collections";
    public static final String PRODUCTS_MEDIA = "products.media";
    public static final String PRODUCTS_INVENTORY = "products.inventory";
    public static final String PRODUCTS_WAREHOUSES = "products.warehouses";
    public static final String PRODUCTS_IMPORT = "products.import";
    public static final String PRODUCTS_EXPORT = "products.export";
    public static final String PRODUCTS_SEO = "products.seo";
    public static final String PRODUCTS_REVIEWS = "products.reviews";

    // Customers
    public static final String CUSTOMERS_LIST = "customers.list";
    public static final String CUSTOMERS_CREATE = "customers.create";
    public static final String CUSTOMERS_EXPORT = "customers.export";
    public static final String CUSTOMERS_WALLET = "customers.wallet";
    public static final String CUSTOMERS_REWARDS = "customers.rewards";

    // Analytics
    public static final String ANALYTICS_DASHBOARD = "analytics.dashboard";
    public static final String ANALYTICS_REPORTS = "analytics.reports";

    // Marketing
    public static final String MARKETING_CAMPAIGNS = "marketing.campaigns";
    public static final String MARKETING_DISCOUNTS = "marketing.discounts";
    public static final String MARKETING_COUPONS = "marketing.coupons";
    public static final String MARKETING_EMAIL = "marketing.email";
    public static final String MARKETING_SEO = "marketing.seo";
    public static final String MARKETING_BLOGS = "marketing.blogs";

    // Finance
    public static final String FINANCE_PAYMENTS = "finance.payments";
    public static final String FINANCE_INVOICES = "finance.invoices";
    public static final String FINANCE_TAXES = "finance.taxes";
    public static final String FINANCE_REFUNDS = "finance.refunds";
    public static final String FINANCE_TRANSACTIONS = "finance.transactions";

    // Shipping
    public static final String SHIPPING_SETTINGS = "shipping.settings";
    public static final String SHIPPING_SHIPMENTS = "shipping.shipments";
    public static final String SHIPPING_COURIERS = "shipping.couriers";
    public static final String SHIPPING_RETURNS = "shipping.returns";

    // Channels
    public static final String APPS_MARKETPLACE = "apps.marketplace";
    public static final String STOREFRONT_THEMES = "storefront.themes";
    public static final String STOREFRONT_PAGES = "storefront.pages";
    public static final String STOREFRONT_NAVIGATION = "storefront.navigation";

    // Settings
    public static final String SETTINGS_GENERAL = "settings.general";
    public static final String SETTINGS_USERS = "settings.users";
    public static final String SETTINGS_BILLING = "settings.billing";
    public static final String SETTINGS_API = "settings.api";

    // Content
    public static final String CONTENT_BLOGS = "content.blogs";
    public static final String CONTENT_PAGES = "content.pages";
    public static final String CONTENT_MEDIA = "content.media";

    /** Default role keys. */
    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_STORE_ADMIN = "store_admin";
    public static final String ROLE_ORDER_MANAGER = "order_manager";
    public static final String ROLE_PRODUCT_MANAGER = "product_manager";
    public static final String ROLE_INVENTORY_MANAGER = "inventory_manager";
    public static final String ROLE_MARKETING_MANAGER = "marketing_manager";
    public static final String ROLE_MANAGER = ROLE_MARKETING_MANAGER;
    public static final String ROLE_CUSTOMER_SUPPORT = "customer_support";
    public static final String ROLE_SUPPORT = ROLE_CUSTOMER_SUPPORT;
    public static final String ROLE_FINANCE = "finance";
    public static final String ROLE_SHIPPING_MANAGER = "shipping_manager";
    public static final String ROLE_CONTENT_MANAGER = "content_manager";
    public static final String ROLE_ANALYST = "analyst";
    public static final String ROLE_VIEWER = "viewer";

    private static final List<PermissionRule> RULES = List.of(
            // Orders
            new PermissionRule(ORDERS_ALL, "Orders", "All orders", "Order list, search, filters, and bulk actions."),
            new PermissionRule(ORDERS_CREATE, "Orders", "Create order", "Create and save manual orders."),
            new PermissionRule(ORDERS_DRAFTS, "Orders", "Draft orders", "Draft orders created for later completion."),
            new PermissionRule(ORDERS_ABANDONED, "Orders", "Abandoned cart", "Started checkouts that were not completed."),
            new PermissionRule(ORDERS_RETURNS, "Orders", "Returns", "Return requests and refunds workflow."),
            new PermissionRule(ORDERS_EXCHANGES, "Orders", "Exchanges", "Exchange requests workflow."),
            new PermissionRule(ORDERS_PURCHASE, "Orders", "Purchase orders", "Supplier purchase orders and receiving."),
            new PermissionRule(ORDERS_BULK, "Orders", "Bulk actions", "Bulk update orders status and details."),
            new PermissionRule(ORDERS_EXPORT, "Orders", "Export orders", "Export order data to CSV/Excel."),

            // Products
            new PermissionRule(PRODUCTS_LIST, "Products", "All products", "Product catalog overview."),
            new PermissionRule(PRODUCTS_CREATE, "Products", "Create product", "Add new products and variants."),
            new PermissionRule(PRODUCTS_CATEGORIES, "Products", "Categories", "Product categories structure."),
            new PermissionRule(PRODUCTS_COLLECTIONS, "Products", "Collections", "Merchandising collections."),
            new PermissionRule(PRODUCTS_MEDIA, "Products", "Media library", "Product images and media assets."),
            new PermissionRule(PRODUCTS_INVENTORY, "Products", "Inventory", "Stock levels across locations."),
            new PermissionRule(PRODUCTS_WAREHOUSES, "Products", "Warehouses", "Fulfilment locations and warehouses."),
            new PermissionRule(PRODUCTS_IMPORT, "Products", "Import products", "Import products from CSV/Excel."),
            new PermissionRule(PRODUCTS_EXPORT, "Products", "Export products", "Export product data to CSV/Excel."),
            new PermissionRule(PRODUCTS_SEO, "Products", "SEO settings", "Product SEO metadata and URLs."),
            new PermissionRule(PRODUCTS_REVIEWS, "Products", "Reviews", "Product reviews and ratings."),

            // Customers
            new PermissionRule(CUSTOMERS_LIST, "Customers", "Customers", "Customer list and history."),
            new PermissionRule(CUSTOMERS_CREATE, "Customers", "Create customer", "Add a customer from scratch."),
            new PermissionRule(CUSTOMERS_EXPORT, "Customers", "Export customers", "Export customer data."),
            new PermissionRule(CUSTOMERS_WALLET, "Customers", "Wallet", "Customer wallet and credits."),
            new PermissionRule(CUSTOMERS_REWARDS, "Customers", "Reward points", "Loyalty and reward points."),

            // Analytics
            new PermissionRule(ANALYTICS_DASHBOARD, "Analytics", "Dashboard", "Reports, charts, and performance insights."),
            new PermissionRule(ANALYTICS_REPORTS, "Analytics", "Reports", "Detailed analytics reports."),

            // Marketing
            new PermissionRule(MARKETING_CAMPAIGNS, "Marketing", "Campaigns", "Campaigns and marketing tools."),
            new PermissionRule(MARKETING_DISCOUNTS, "Marketing", "Discounts", "Discount codes and automatic discounts."),
            new PermissionRule(MARKETING_COUPONS, "Marketing", "Coupons", "Coupon management."),
            new PermissionRule(MARKETING_EMAIL, "Marketing", "Email marketing", "Email campaigns and automation."),
            new PermissionRule(MARKETING_SEO, "Marketing", "SEO", "SEO settings and optimization."),
            new PermissionRule(MARKETING_BLOGS, "Marketing", "Blogs", "Blog posts and content."),

            // Finance
            new PermissionRule(FINANCE_PAYMENTS, "Finance", "Payments", "Gateway settings and payment status views."),
            new PermissionRule(FINANCE_INVOICES, "Finance", "Invoices", "Invoice records and downloads."),
            new PermissionRule(FINANCE_TAXES, "Finance", "Taxes", "Tax settings and GST."),
            new PermissionRule(FINANCE_REFUNDS, "Finance", "Refunds", "Refund requests and processing."),
            new PermissionRule(FINANCE_TRANSACTIONS, "Finance", "Transactions", "Transaction history and logs."),

            // Shipping
            new PermissionRule(SHIPPING_SETTINGS, "Shipping", "Settings", "Delivery rules, carriers, and methods."),
            new PermissionRule(SHIPPING_SHIPMENTS, "Shipping", "Shipments", "Shipment tracking and labels."),
            new PermissionRule(SHIPPING_COURIERS, "Shipping", "Couriers", "Courier management and assignment."),
            new PermissionRule(SHIPPING_RETURNS, "Shipping", "Returns", "Return shipping and exchange."),

            // Channels
            new PermissionRule(APPS_MARKETPLACE, "Channels", "Apps", "App marketplace and integrations."),
            new PermissionRule(STOREFRONT_THEMES, "Channels", "Themes", "Themes and storefront design."),
            new PermissionRule(STOREFRONT_PAGES, "Channels", "Pages", "Landing pages and content pages."),
            new PermissionRule(STOREFRONT_NAVIGATION, "Channels", "Navigation", "Menu and navigation structure."),

            // Settings
            new PermissionRule(SETTINGS_GENERAL, "Settings", "General settings", "Store-wide configuration pages."),
            new PermissionRule(SETTINGS_USERS, "Settings", "Users & roles", "Invite members and manage access."),
            new PermissionRule(SETTINGS_BILLING, "Settings", "Billing", "Subscription and billing management."),
            new PermissionRule(SETTINGS_API, "Settings", "API keys", "API credentials and webhooks."),

            // Content
            new PermissionRule(CONTENT_BLOGS, "Content", "Blogs", "Blog posts and articles."),
            new PermissionRule(CONTENT_PAGES, "Content", "Pages", "Static and landing pages."),
            new PermissionRule(CONTENT_MEDIA, "Content", "Media", "Media library and assets."));

    private PermissionCatalog() {}

    public static List<PermissionRule> rules() {
        return RULES;
    }

    /** All permission keys in catalog order. */
    public static List<String> keys() {
        List<String> keys = new ArrayList<>(RULES.size());
        for (PermissionRule rule : RULES) {
            keys.add(rule.key());
        }
        return keys;
    }

    /** A matrix assigning {@code level} to every catalog key (used for the Owner role). */
    public static Map<String, AccessLevel> uniformMatrix(AccessLevel level) {
        Map<String, AccessLevel> matrix = new LinkedHashMap<>();
        for (PermissionRule rule : RULES) {
            matrix.put(rule.key(), level);
        }
        return matrix;
    }

    /** The twelve built-in role templates, seeded into every new store. */
    public static List<RoleTemplate> defaultRoles() {
        return List.of(
                new RoleTemplate(ROLE_OWNER, "Store Owner", "Full control across the workspace.", true, true,
                        uniformMatrix(AccessLevel.MANAGE)),
                new RoleTemplate(ROLE_STORE_ADMIN, "Store Admin", "Almost everything. Cannot delete store or transfer ownership.", true, false,
                        storeAdminMatrix()),
                new RoleTemplate(ROLE_ORDER_MANAGER, "Order Manager", "Manages orders, returns, and shipments.", true, false,
                        orderManagerMatrix()),
                new RoleTemplate(ROLE_PRODUCT_MANAGER, "Product Manager", "Manages products, collections, and inventory.", true, false,
                        productManagerMatrix()),
                new RoleTemplate(ROLE_INVENTORY_MANAGER, "Inventory Manager", "Manages stock, warehouses, and purchase orders.", true, false,
                        inventoryManagerMatrix()),
                new RoleTemplate(ROLE_MARKETING_MANAGER, "Marketing Manager", "Campaigns, discounts, and SEO.", true, false,
                        marketingManagerMatrix()),
                new RoleTemplate(ROLE_CUSTOMER_SUPPORT, "Customer Support", "Helps customers with orders and returns.", true, false,
                        customerSupportMatrix()),
                new RoleTemplate(ROLE_FINANCE, "Finance", "Payments, invoices, taxes, and refunds.", true, false,
                        financeMatrix()),
                new RoleTemplate(ROLE_SHIPPING_MANAGER, "Shipping Manager", "Shipments, couriers, and returns.", true, false,
                        shippingManagerMatrix()),
                new RoleTemplate(ROLE_CONTENT_MANAGER, "Content Manager", "Blogs, pages, themes, and media.", true, false,
                        contentManagerMatrix()),
                new RoleTemplate(ROLE_ANALYST, "Analyst", "Read-only access to all operational pages.", true, false,
                        analystMatrix()),
                new RoleTemplate(ROLE_VIEWER, "Viewer", "Basic read-only access.", true, false,
                        viewerMatrix()));
    }

    private static Map<String, AccessLevel> baseMatrix() {
        return uniformMatrix(AccessLevel.NONE);
    }

    private static Map<String, AccessLevel> storeAdminMatrix() {
        Map<String, AccessLevel> m = uniformMatrix(AccessLevel.MANAGE);
        // Restrict dangerous settings
        m.put(SETTINGS_BILLING, AccessLevel.VIEW);
        m.put(SETTINGS_API, AccessLevel.VIEW);
        return m;
    }

    private static Map<String, AccessLevel> orderManagerMatrix() {
        Map<String, AccessLevel> m = baseMatrix();
        m.put(ORDERS_ALL, AccessLevel.MANAGE);
        m.put(ORDERS_CREATE, AccessLevel.MANAGE);
        m.put(ORDERS_DRAFTS, AccessLevel.MANAGE);
        m.put(ORDERS_ABANDONED, AccessLevel.VIEW);
        m.put(ORDERS_RETURNS, AccessLevel.MANAGE);
        m.put(ORDERS_EXCHANGES, AccessLevel.MANAGE);
        m.put(ORDERS_PURCHASE, AccessLevel.VIEW);
        m.put(ORDERS_BULK, AccessLevel.MANAGE);
        m.put(ORDERS_EXPORT, AccessLevel.VIEW);
        m.put(CUSTOMERS_LIST, AccessLevel.VIEW);
        m.put(ANALYTICS_DASHBOARD, AccessLevel.VIEW);
        m.put(SHIPPING_SHIPMENTS, AccessLevel.MANAGE);
        m.put(SHIPPING_COURIERS, AccessLevel.VIEW);
        m.put(SHIPPING_RETURNS, AccessLevel.MANAGE);
        m.put(FINANCE_INVOICES, AccessLevel.VIEW);
        return m;
    }

    private static Map<String, AccessLevel> productManagerMatrix() {
        Map<String, AccessLevel> m = baseMatrix();
        m.put(PRODUCTS_LIST, AccessLevel.MANAGE);
        m.put(PRODUCTS_CREATE, AccessLevel.MANAGE);
        m.put(PRODUCTS_CATEGORIES, AccessLevel.MANAGE);
        m.put(PRODUCTS_COLLECTIONS, AccessLevel.MANAGE);
        m.put(PRODUCTS_MEDIA, AccessLevel.MANAGE);
        m.put(PRODUCTS_INVENTORY, AccessLevel.MANAGE);
        m.put(PRODUCTS_WAREHOUSES, AccessLevel.VIEW);
        m.put(PRODUCTS_IMPORT, AccessLevel.MANAGE);
        m.put(PRODUCTS_EXPORT, AccessLevel.VIEW);
        m.put(PRODUCTS_SEO, AccessLevel.MANAGE);
        m.put(PRODUCTS_REVIEWS, AccessLevel.VIEW);
        m.put(CONTENT_MEDIA, AccessLevel.VIEW);
        return m;
    }

    private static Map<String, AccessLevel> inventoryManagerMatrix() {
        Map<String, AccessLevel> m = baseMatrix();
        m.put(PRODUCTS_INVENTORY, AccessLevel.MANAGE);
        m.put(PRODUCTS_WAREHOUSES, AccessLevel.MANAGE);
        m.put(PRODUCTS_LIST, AccessLevel.VIEW);
        m.put(ORDERS_PURCHASE, AccessLevel.MANAGE);
        m.put(PRODUCTS_IMPORT, AccessLevel.MANAGE);
        m.put(PRODUCTS_EXPORT, AccessLevel.VIEW);
        return m;
    }

    private static Map<String, AccessLevel> marketingManagerMatrix() {
        Map<String, AccessLevel> m = baseMatrix();
        m.put(MARKETING_CAMPAIGNS, AccessLevel.MANAGE);
        m.put(MARKETING_DISCOUNTS, AccessLevel.MANAGE);
        m.put(MARKETING_COUPONS, AccessLevel.MANAGE);
        m.put(MARKETING_EMAIL, AccessLevel.MANAGE);
        m.put(MARKETING_SEO, AccessLevel.MANAGE);
        m.put(MARKETING_BLOGS, AccessLevel.MANAGE);
        m.put(CONTENT_BLOGS, AccessLevel.MANAGE);
        m.put(CONTENT_PAGES, AccessLevel.MANAGE);
        m.put(STOREFRONT_PAGES, AccessLevel.VIEW);
        m.put(ORDERS_ALL, AccessLevel.VIEW);
        m.put(PRODUCTS_LIST, AccessLevel.VIEW);
        m.put(CUSTOMERS_LIST, AccessLevel.VIEW);
        m.put(ANALYTICS_DASHBOARD, AccessLevel.VIEW);
        m.put(ANALYTICS_REPORTS, AccessLevel.VIEW);
        return m;
    }

    private static Map<String, AccessLevel> customerSupportMatrix() {
        Map<String, AccessLevel> m = baseMatrix();
        m.put(CUSTOMERS_LIST, AccessLevel.MANAGE);
        m.put(CUSTOMERS_CREATE, AccessLevel.VIEW);
        m.put(ORDERS_ALL, AccessLevel.VIEW);
        m.put(ORDERS_RETURNS, AccessLevel.MANAGE);
        m.put(ORDERS_EXCHANGES, AccessLevel.MANAGE);
        m.put(SHIPPING_SHIPMENTS, AccessLevel.VIEW);
        m.put(SHIPPING_RETURNS, AccessLevel.VIEW);
        m.put(FINANCE_INVOICES, AccessLevel.VIEW);
        m.put(FINANCE_REFUNDS, AccessLevel.VIEW);
        return m;
    }

    private static Map<String, AccessLevel> financeMatrix() {
        Map<String, AccessLevel> m = baseMatrix();
        m.put(FINANCE_PAYMENTS, AccessLevel.MANAGE);
        m.put(FINANCE_INVOICES, AccessLevel.MANAGE);
        m.put(FINANCE_TAXES, AccessLevel.MANAGE);
        m.put(FINANCE_REFUNDS, AccessLevel.MANAGE);
        m.put(FINANCE_TRANSACTIONS, AccessLevel.MANAGE);
        m.put(ORDERS_ALL, AccessLevel.VIEW);
        m.put(ANALYTICS_DASHBOARD, AccessLevel.VIEW);
        m.put(ANALYTICS_REPORTS, AccessLevel.VIEW);
        return m;
    }

    private static Map<String, AccessLevel> shippingManagerMatrix() {
        Map<String, AccessLevel> m = baseMatrix();
        m.put(SHIPPING_SETTINGS, AccessLevel.MANAGE);
        m.put(SHIPPING_SHIPMENTS, AccessLevel.MANAGE);
        m.put(SHIPPING_COURIERS, AccessLevel.MANAGE);
        m.put(SHIPPING_RETURNS, AccessLevel.MANAGE);
        m.put(ORDERS_ALL, AccessLevel.VIEW);
        m.put(ORDERS_RETURNS, AccessLevel.VIEW);
        m.put(ORDERS_EXCHANGES, AccessLevel.VIEW);
        return m;
    }

    private static Map<String, AccessLevel> contentManagerMatrix() {
        Map<String, AccessLevel> m = baseMatrix();
        m.put(CONTENT_BLOGS, AccessLevel.MANAGE);
        m.put(CONTENT_PAGES, AccessLevel.MANAGE);
        m.put(CONTENT_MEDIA, AccessLevel.MANAGE);
        m.put(STOREFRONT_THEMES, AccessLevel.MANAGE);
        m.put(STOREFRONT_PAGES, AccessLevel.MANAGE);
        m.put(STOREFRONT_NAVIGATION, AccessLevel.MANAGE);
        m.put(MARKETING_SEO, AccessLevel.MANAGE);
        m.put(MARKETING_BLOGS, AccessLevel.VIEW);
        m.put(PRODUCTS_MEDIA, AccessLevel.VIEW);
        return m;
    }

    private static Map<String, AccessLevel> analystMatrix() {
        Map<String, AccessLevel> m = baseMatrix();
        m.put(ORDERS_ALL, AccessLevel.VIEW);
        m.put(PRODUCTS_LIST, AccessLevel.VIEW);
        m.put(CUSTOMERS_LIST, AccessLevel.VIEW);
        m.put(ANALYTICS_DASHBOARD, AccessLevel.VIEW);
        m.put(ANALYTICS_REPORTS, AccessLevel.VIEW);
        m.put(FINANCE_PAYMENTS, AccessLevel.VIEW);
        m.put(FINANCE_INVOICES, AccessLevel.VIEW);
        m.put(FINANCE_TRANSACTIONS, AccessLevel.VIEW);
        m.put(SHIPPING_SHIPMENTS, AccessLevel.VIEW);
        m.put(MARKETING_CAMPAIGNS, AccessLevel.VIEW);
        return m;
    }

    private static Map<String, AccessLevel> viewerMatrix() {
        Map<String, AccessLevel> m = baseMatrix();
        m.put(ORDERS_ALL, AccessLevel.VIEW);
        m.put(PRODUCTS_LIST, AccessLevel.VIEW);
        m.put(CUSTOMERS_LIST, AccessLevel.VIEW);
        m.put(ANALYTICS_DASHBOARD, AccessLevel.VIEW);
        return m;
    }
}
