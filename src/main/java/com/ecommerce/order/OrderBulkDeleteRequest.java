package com.ecommerce.order;

import java.util.List;

public record OrderBulkDeleteRequest(List<String> ids) {}
