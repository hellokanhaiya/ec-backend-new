package com.ecommerce.plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * The canonical taxonomy of plugin API scopes — boolean capabilities a plugin token can hold,
 * parallel to (not merged with) {@link com.ecommerce.access.PermissionCatalog}, which stays the
 * human/RBAC page taxonomy. The catalog is read/export/document scopes plus narrowly-scoped
 * plugin-owned writes: metafields (locked to the app's own namespace) and product SEO fields
 * (a dedicated three-field endpoint). Plugin tokens can never be granted order
 * create/edit/dispatch, pricing/inventory writes, user creation, or any other admin write.
 * Groups mirror the dashboard permission groups for a familiar picker.
 */
public final class PluginScopeCatalog {

    // Orders
    public static final String ORDERS_READ = "orders:read";
    public static final String ORDERS_EXPORT = "orders:export";
    public static final String LABELS_GENERATE = "labels:generate";
    public static final String INVOICES_GENERATE = "invoices:generate";
    public static final String PURCHASE_ORDERS_READ = "purchase-orders:read";
    public static final String PURCHASE_ORDERS_EXPORT = "purchase-orders:export";
    public static final String ABANDONED_CARTS_READ = "abandoned-carts:read";

    // Products
    public static final String PRODUCTS_READ = "products:read";
    public static final String PRODUCTS_EXPORT = "products:export";
    public static final String CATEGORIES_READ = "categories:read";
    public static final String BUNDLES_READ = "bundles:read";
    public static final String INVENTORY_READ = "inventory:read";
    public static final String WAREHOUSES_READ = "warehouses:read";
    public static final String MEDIA_READ = "media:read";
    public static final String TAGS_READ = "tags:read";

    // Customers
    public static final String CUSTOMERS_READ = "customers:read";
    public static final String CUSTOMERS_EXPORT = "customers:export";

    // Analytics
    public static final String ANALYTICS_READ = "analytics:read";

    // Marketing
    public static final String DISCOUNTS_READ = "discounts:read";

    // Finance
    public static final String BILLING_READ = "billing:read";
    public static final String TAX_READ = "tax:read";

    // Shipping
    public static final String SHIPPING_READ = "shipping:read";

    // Marketplace
    public static final String VENDORS_READ = "vendors:read";

    // Plugin-owned writes
    public static final String METAFIELDS_READ = "metafields:read";
    public static final String METAFIELDS_WRITE = "metafields:write";
    public static final String PRODUCTS_WRITE_SEO = "products:write-seo";

    private static final List<PluginScopeRule> RULES = List.of(
            // Orders
            new PluginScopeRule(ORDERS_READ, "Orders", "Read orders",
                    "List orders and view order details."),
            new PluginScopeRule(ORDERS_EXPORT, "Orders", "Export orders",
                    "Export order data to CSV."),
            new PluginScopeRule(LABELS_GENERATE, "Orders", "Shipping labels",
                    "Generate shipping label PDFs for orders."),
            new PluginScopeRule(INVOICES_GENERATE, "Orders", "Invoices",
                    "Generate invoice PDFs for orders."),
            new PluginScopeRule(PURCHASE_ORDERS_READ, "Orders", "Read purchase orders",
                    "List purchase orders and suppliers, and view details."),
            new PluginScopeRule(PURCHASE_ORDERS_EXPORT, "Orders", "Export purchase orders",
                    "Export purchase order data to CSV."),
            new PluginScopeRule(ABANDONED_CARTS_READ, "Orders", "Read abandoned carts",
                    "List abandoned carts and view details."),

            // Products
            new PluginScopeRule(PRODUCTS_READ, "Products", "Read products",
                    "List products and view product details."),
            new PluginScopeRule(PRODUCTS_EXPORT, "Products", "Export products",
                    "Export product data to CSV."),
            new PluginScopeRule(CATEGORIES_READ, "Products", "Read categories",
                    "List categories and view category details."),
            new PluginScopeRule(BUNDLES_READ, "Products", "Read bundles",
                    "List product bundles and view details."),
            new PluginScopeRule(INVENTORY_READ, "Products", "Read inventory",
                    "View stock levels across warehouses."),
            new PluginScopeRule(WAREHOUSES_READ, "Products", "Read warehouses",
                    "List warehouses and view details."),
            new PluginScopeRule(MEDIA_READ, "Products", "Read media",
                    "List media library assets and view details."),
            new PluginScopeRule(TAGS_READ, "Products", "Read tags",
                    "List store tags."),

            // Customers
            new PluginScopeRule(CUSTOMERS_READ, "Customers", "Read customers",
                    "List customers and view customer details."),
            new PluginScopeRule(CUSTOMERS_EXPORT, "Customers", "Export customers",
                    "Export customer data."),

            // Analytics
            new PluginScopeRule(ANALYTICS_READ, "Analytics", "Read analytics",
                    "Dashboard stats, KPIs, revenue, traffic, and sales breakdowns."),

            // Marketing
            new PluginScopeRule(DISCOUNTS_READ, "Marketing", "Read discounts",
                    "List discounts and coupons and view details."),

            // Finance
            new PluginScopeRule(BILLING_READ, "Finance", "Read billing",
                    "View subscription, plans, and entitlements."),
            new PluginScopeRule(TAX_READ, "Finance", "Read taxes",
                    "View tax rates for the store's country."),

            // Shipping
            new PluginScopeRule(SHIPPING_READ, "Shipping", "Read shipping",
                    "View delivery settings, zones, rates, and serviceability."),

            // Marketplace
            new PluginScopeRule(VENDORS_READ, "Marketplace", "Read vendors",
                    "List marketplace vendors and view details."),

            // Plugin data
            new PluginScopeRule(METAFIELDS_READ, "Plugin data", "Read metafields",
                    "Read this app's own metafields on products, orders, and app settings."),
            new PluginScopeRule(METAFIELDS_WRITE, "Plugin data", "Write metafields",
                    "Write this app's own metafields on products, orders, and app settings."),
            new PluginScopeRule(PRODUCTS_WRITE_SEO, "Plugin data", "Write product SEO",
                    "Update product SEO title, SEO description, and slug — no other product fields."));

    private PluginScopeCatalog() {}

    public static List<PluginScopeRule> rules() {
        return RULES;
    }

    /** All scope keys in catalog order. */
    public static List<String> keys() {
        List<String> keys = new ArrayList<>(RULES.size());
        for (PluginScopeRule rule : RULES) {
            keys.add(rule.key());
        }
        return keys;
    }

    public static boolean isValid(String key) {
        for (PluginScopeRule rule : RULES) {
            if (rule.key().equals(key)) {
                return true;
            }
        }
        return false;
    }
}
