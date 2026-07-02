package com.ecommerce.customer;

import java.util.List;

public record ImportRequest(boolean overwrite, List<CustomerImportRow> rows) {}
