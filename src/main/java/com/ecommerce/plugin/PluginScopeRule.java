package com.ecommerce.plugin;

/** One entry in the plugin scope catalog — an API capability a plugin token can be granted. */
public record PluginScopeRule(String key, String group, String label, String description) {}
