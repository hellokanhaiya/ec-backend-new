package com.ecommerce.bulk;

import com.ecommerce.product.ProductData;
import com.ecommerce.product.ProductImageData;
import com.ecommerce.product.ProductImageRequest;
import com.ecommerce.product.ProductRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Reads and writes the product CSV format. Hand-rolled (no third-party CSV
 * dependency) but RFC-4180 compliant: fields containing commas, quotes or
 * newlines are wrapped in double quotes with embedded quotes doubled. Multi-value
 * columns (tags, images) are pipe-separated.
 */
@Service
public class ProductCsvService {

    /** Canonical column order for both export and import. */
    static final List<String> COLUMNS = List.of(
            "sku",
            "title",
            "slug",
            "summary",
            "description",
            "status",
            "productType",
            "category",
            "categoryPath",
            "categoryPublicId",
            "vendor",
            "price",
            "compareAtPrice",
            "costPerItem",
            "barcode",
            "stock",
            "trackInventory",
            "requiresShipping",
            "weight",
            "length",
            "width",
            "height",
            "countryOfOrigin",
            "taxable",
            "hsnCode",
            "taxCode",
            "taxRate",
            "seoTitle",
            "seoDescription",
            "seoKeyword",
            "tags",
            "images");

    // ---- Writing (export) ----------------------------------------------------

    public byte[] write(List<ProductData> products) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", COLUMNS)).append("\r\n");
        for (ProductData p : products) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("sku", p.sku());
            row.put("title", p.title());
            row.put("slug", p.slug());
            row.put("summary", p.summary());
            row.put("description", p.description());
            row.put("status", p.status());
            row.put("productType", p.productType());
            row.put("category", p.category());
            row.put("categoryPath", p.categoryPath());
            row.put("categoryPublicId", p.categoryPublicId());
            row.put("vendor", p.vendor());
            row.put("price", str(p.price()));
            row.put("compareAtPrice", str(p.compareAtPrice()));
            row.put("costPerItem", str(p.costPerItem()));
            row.put("barcode", p.barcode());
            row.put("stock", p.stock() == null ? "" : String.valueOf(p.stock()));
            row.put("trackInventory", String.valueOf(p.trackInventory()));
            row.put("requiresShipping", String.valueOf(p.requiresShipping()));
            row.put("weight", str(p.weight()));
            row.put("length", str(p.length()));
            row.put("width", str(p.width()));
            row.put("height", str(p.height()));
            row.put("countryOfOrigin", p.countryOfOrigin());
            row.put("taxable", String.valueOf(p.taxable()));
            row.put("hsnCode", p.hsnCode());
            row.put("taxCode", p.taxCode());
            row.put("taxRate", str(p.taxRate()));
            row.put("seoTitle", p.seoTitle());
            row.put("seoDescription", p.seoDescription());
            row.put("seoKeyword", p.seoKeyword());
            row.put("tags", p.tags() == null ? "" : String.join("|", p.tags()));
            row.put(
                    "images",
                    p.images() == null
                            ? ""
                            : String.join(
                                    "|",
                                    p.images().stream()
                                            .map(ProductImageData::url)
                                            .filter(url -> url != null && !url.isBlank())
                                            .toList()));

            List<String> cells = new ArrayList<>(COLUMNS.size());
            for (String column : COLUMNS) {
                cells.add(escape(row.get(column)));
            }
            sb.append(String.join(",", cells)).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String str(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuote =
                value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!needsQuote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    // ---- Reading (import) ----------------------------------------------------

    /** Parse raw CSV bytes into a header-keyed list of rows. Returns an empty list
     * when there is no data (only a header, or nothing at all). */
    public List<Map<String, String>> parse(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.startsWith("﻿")) {
            content = content.substring(1); // strip BOM
        }
        List<List<String>> records = parseRecords(content);
        List<Map<String, String>> rows = new ArrayList<>();
        if (records.isEmpty()) {
            return rows;
        }
        List<String> header = records.get(0).stream()
                .map(h -> h == null ? "" : h.trim())
                .toList();
        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            if (record.stream().allMatch(cell -> cell == null || cell.isBlank())) {
                continue; // skip fully blank lines
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                String key = header.get(c);
                if (key.isEmpty()) {
                    continue;
                }
                String value = c < record.size() ? record.get(c) : "";
                row.put(key.toLowerCase(Locale.ROOT), value);
            }
            rows.add(row);
        }
        return rows;
    }

    /** State-machine CSV reader handling quoted fields with embedded delimiters. */
    private static List<List<String>> parseRecords(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int n = content.length();
        for (int i = 0; i < n; i++) {
            char ch = content.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < n && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
            } else {
                switch (ch) {
                    case '"' -> inQuotes = true;
                    case ',' -> {
                        current.add(field.toString());
                        field.setLength(0);
                    }
                    case '\r' -> {
                        // swallow; the following \n (if any) ends the record
                    }
                    case '\n' -> {
                        current.add(field.toString());
                        field.setLength(0);
                        records.add(current);
                        current = new ArrayList<>();
                    }
                    default -> field.append(ch);
                }
            }
        }
        // flush trailing field/record if the file does not end with a newline
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            records.add(current);
        }
        return records;
    }

    /** Map a parsed row to a {@link ProductRequest} for create/update. */
    public ProductRequest toRequest(Map<String, String> row) {
        String title = trimToNull(row.get("title"));
        String tagsRaw = row.get("tags");
        List<String> tags = tagsRaw == null || tagsRaw.isBlank()
                ? List.of()
                : splitPipes(tagsRaw);

        String imagesRaw = row.get("images");
        List<ProductImageRequest> images = new ArrayList<>();
        if (imagesRaw != null && !imagesRaw.isBlank()) {
            List<String> urls = splitPipes(imagesRaw);
            for (int i = 0; i < urls.size(); i++) {
                images.add(new ProductImageRequest(urls.get(i), null, i, i == 0));
            }
        }

        return new ProductRequest(
                title,
                trimToNull(row.get("slug")),
                trimToNull(row.get("summary")),
                trimToNull(row.get("description")),
                trimToNull(row.get("status")),
                trimToNull(row.get("producttype")),
                trimToNull(row.get("category")),
                trimToNull(row.get("categorypath")),
                trimToNull(row.get("categorypublicid")),
                trimToNull(row.get("vendor")),
                decimal(row.get("price")),
                decimal(row.get("compareatprice")),
                decimal(row.get("costperitem")),
                trimToNull(row.get("sku")),
                trimToNull(row.get("barcode")),
                integer(row.get("stock")),
                bool(row.get("trackinventory")),
                bool(row.get("requiresshipping")),
                decimal(row.get("weight")),
                decimal(row.get("length")),
                decimal(row.get("width")),
                decimal(row.get("height")),
                trimToNull(row.get("countryoforigin")),
                bool(row.get("taxable")),
                trimToNull(row.get("hsncode")),
                trimToNull(row.get("taxcode")),
                decimal(row.get("taxrate")),
                trimToNull(row.get("seotitle")),
                trimToNull(row.get("seodescription")),
                trimToNull(row.get("seokeyword")),
                null,
                tags,
                images,
                null,
                null);
    }

    private static List<String> splitPipes(String value) {
        List<String> out = new ArrayList<>();
        for (String part : value.split("\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BigDecimal decimal(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return new BigDecimal(trimmed.replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer integer(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(trimmed.replace(",", "")));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Boolean bool(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("true") || lower.equals("1") || lower.equals("yes") || lower.equals("y")) {
            return Boolean.TRUE;
        }
        if (lower.equals("false") || lower.equals("0") || lower.equals("no") || lower.equals("n")) {
            return Boolean.FALSE;
        }
        return null;
    }
}
