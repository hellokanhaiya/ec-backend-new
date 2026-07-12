package com.ecommerce.theme;

import com.ecommerce.theme.ThemeDtos.CreateThemeRequest;
import com.ecommerce.theme.ThemeDtos.ThemeData;
import com.ecommerce.theme.ThemeDtos.ThemeVersionData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Persistence and versioning for storefront themes. All operations are scoped to
 * a single store id (resolved by the controller from the caller's access scope),
 * so a merchant can only ever touch their own themes.
 */
@Service
public class ThemeService {
    private final ThemeRepository themes;
    private final ThemeVersionRepository versions;
    private final ObjectMapper mapper;

    public ThemeService(ThemeRepository themes, ThemeVersionRepository versions, ObjectMapper mapper) {
        this.themes = themes;
        this.versions = versions;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ThemeData> list(String storeId) {
        return themes.findByStoreIdOrderByCreatedAtAsc(storeId).stream().map(this::toData).toList();
    }

    @Transactional(readOnly = true)
    public ThemeData get(String storeId, String id) {
        return toData(require(storeId, id));
    }

    @Transactional
    public ThemeData create(String storeId, CreateThemeRequest req) {
        ThemeEntity t = new ThemeEntity();
        t.setId(UUID.randomUUID().toString());
        t.setStoreId(storeId);
        t.setName(orDefault(req.name(), "Custom theme"));
        t.setAuthor(orDefault(req.author(), "You"));
        t.setAccent(orDefault(req.accent(), "#4f46e5"));
        t.setActive(false);
        t.setDraftJson(write(req.draft()));
        stamp(t);
        return toData(themes.save(t));
    }

    @Transactional
    public ThemeData duplicate(String storeId, String id) {
        ThemeEntity src = require(storeId, id);
        ThemeEntity copy = new ThemeEntity();
        copy.setId(UUID.randomUUID().toString());
        copy.setStoreId(storeId);
        copy.setName(src.getName() + " copy");
        copy.setAuthor(src.getAuthor());
        copy.setAccent(src.getAccent());
        copy.setActive(false);
        copy.setDraftJson(src.getDraftJson());
        copy.setPublishedJson(null);
        stamp(copy);
        return toData(themes.save(copy));
    }

    @Transactional
    public void delete(String storeId, String id) {
        ThemeEntity t = require(storeId, id);
        if (t.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can't delete the active theme");
        }
        versions.deleteByThemeId(id);
        themes.delete(t);
    }

    @Transactional
    public ThemeData saveDraft(String storeId, String id, JsonNode draft) {
        ThemeEntity t = require(storeId, id);
        t.setDraftJson(write(draft));
        t.setUpdatedAt(Instant.now());
        return toData(themes.save(t));
    }

    @Transactional
    public ThemeData publish(String storeId, String id, JsonNode draft, String label) {
        ThemeEntity t = require(storeId, id);
        String json = write(draft);
        t.setDraftJson(json);
        t.setPublishedJson(json);
        t.setActive(true);
        t.setUpdatedAt(Instant.now());
        // Only one active theme per store.
        for (ThemeEntity other : themes.findByStoreIdAndActiveTrue(storeId)) {
            if (!other.getId().equals(id)) {
                other.setActive(false);
                themes.save(other);
            }
        }
        themes.save(t);
        ThemeVersionEntity v = new ThemeVersionEntity();
        v.setId(UUID.randomUUID().toString());
        v.setThemeId(id);
        v.setStoreId(storeId);
        v.setLabel(orDefault(label, "Published"));
        v.setPublished(true);
        v.setJson(json);
        v.setCreatedAt(Instant.now());
        versions.save(v);
        return toData(t);
    }

    @Transactional
    public ThemeData activate(String storeId, String id) {
        ThemeEntity t = require(storeId, id);
        if (t.getPublishedJson() == null) {
            t.setPublishedJson(t.getDraftJson());
        }
        t.setActive(true);
        t.setUpdatedAt(Instant.now());
        for (ThemeEntity other : themes.findByStoreIdAndActiveTrue(storeId)) {
            if (!other.getId().equals(id)) {
                other.setActive(false);
                themes.save(other);
            }
        }
        return toData(themes.save(t));
    }

    @Transactional(readOnly = true)
    public List<ThemeVersionData> listVersions(String storeId, String id) {
        require(storeId, id);
        return versions.findByThemeIdOrderByCreatedAtDesc(id).stream()
                .map(v -> new ThemeVersionData(v.getId(), v.getLabel(), v.isPublished(), v.getCreatedAt().toString()))
                .toList();
    }

    @Transactional
    public ThemeData rollback(String storeId, String id, String versionId) {
        ThemeEntity t = require(storeId, id);
        ThemeVersionEntity v = versions
                .findByIdAndThemeId(versionId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));
        t.setDraftJson(v.getJson());
        t.setUpdatedAt(Instant.now());
        return toData(themes.save(t));
    }

    /* ── helpers ─────────────────────────────────────────────────────────── */

    private ThemeEntity require(String storeId, String id) {
        return themes
                .findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Theme not found"));
    }

    private void stamp(ThemeEntity t) {
        Instant now = Instant.now();
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
    }

    private ThemeData toData(ThemeEntity t) {
        return new ThemeData(
                t.getId(),
                t.getName(),
                t.getAuthor(),
                t.getAccent(),
                t.isActive(),
                read(t.getDraftJson()),
                read(t.getPublishedJson()),
                t.getUpdatedAt().toString());
    }

    private String write(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Theme document is required");
        }
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid theme document");
        }
    }

    private Object read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
