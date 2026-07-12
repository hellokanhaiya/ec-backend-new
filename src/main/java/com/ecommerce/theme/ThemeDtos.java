package com.ecommerce.theme;

import com.fasterxml.jackson.databind.JsonNode;

/** Request/response shapes for the storefront theme API. */
public final class ThemeDtos {
    private ThemeDtos() {}

    /** A theme returned to the builder. {@code draft}/{@code published} are the raw JSON docs. */
    public record ThemeData(
            String id,
            String name,
            String author,
            String accent,
            boolean active,
            Object draft,
            Object published,
            String updatedAt) {}

    public record ThemeVersionData(String id, String label, boolean published, String createdAt) {}

    public record CreateThemeRequest(String name, String author, String accent, JsonNode draft) {}

    public record SaveDraftRequest(JsonNode draft) {}

    public record PublishRequest(JsonNode draft, String label) {}
}
