package com.ecommerce.category;

import java.util.List;

/** Ordered list of publicCategoryIds; the position of each id becomes its new
 * sortPosition. Typically the ids of one sibling group being rearranged. */
public record CategoryReorderRequest(List<String> ids) {}
