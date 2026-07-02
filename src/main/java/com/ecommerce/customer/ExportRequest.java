package com.ecommerce.customer;

import java.util.List;

/** Which customers to export. Null/empty {@code ids} = the whole store. */
public record ExportRequest(List<String> ids) {}
