package com.ecommerce.access;

/** One entry in the permission catalog — a page/capability that access can be granted on. */
public record PermissionRule(String key, String group, String label, String description) {}
