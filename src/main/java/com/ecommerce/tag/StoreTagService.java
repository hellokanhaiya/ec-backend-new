package com.ecommerce.tag;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StoreTagService {
    private final TagRepository tagRepository;

    public StoreTagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public List<TagData> list(String storeId) {
        return tagRepository.findByStoreIdOrderByNameAsc(storeId).stream()
                .map(tag -> new TagData(tag.getId(), tag.getName()))
                .toList();
    }

    public TagData upsert(String storeId, String name) {
        Tag tag = findOrCreate(storeId, name);
        return new TagData(tag.getId(), tag.getName());
    }

    /** Find a store tag by name (case-insensitive) or create it. This is the shared upsert used both
     * by the tags endpoint and when tags are added while editing a customer. */
    public Tag findOrCreate(String storeId, String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Tag name is required");
        }
        String name = rawName.trim().replaceAll("\\s+", " ");
        return tagRepository.findByStoreIdAndNameIgnoreCase(storeId, name)
                .orElseGet(() -> {
                    Tag tag = new Tag();
                    tag.setStoreId(storeId);
                    tag.setName(name);
                    return tagRepository.save(tag);
                });
    }
}
