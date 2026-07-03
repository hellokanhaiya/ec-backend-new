package com.ecommerce.media;

import java.util.List;

public record MediaListData(List<MediaData> items, long total, int page, int size) {}
