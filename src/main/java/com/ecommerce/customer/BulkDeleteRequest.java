package com.ecommerce.customer;

import java.util.List;

public record BulkDeleteRequest(List<String> ids) {}
