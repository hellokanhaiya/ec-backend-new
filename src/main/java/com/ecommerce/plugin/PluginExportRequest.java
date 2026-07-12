package com.ecommerce.plugin;

import java.util.List;

/**
 * Optional body for plugin export endpoints. {@code ids} selects specific records (empty/null =
 * all); {@code skus} is used only by the product export.
 */
public record PluginExportRequest(List<String> ids, List<String> skus) {}
