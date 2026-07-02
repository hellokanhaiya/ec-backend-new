package com.ecommerce.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.tag.StoreTagService;
import com.ecommerce.tag.TagRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.web.server.ResponseStatusException;

/**
 * Isolated persistence tests for {@link StoreCategoryService} against embedded H2.
 * The {@code spring.config.import=} override disables the optional {@code .env}
 * file so the test never picks up the developer's MySQL datasource.
 */
@DataJpaTest(
        properties = {
            "spring.config.import=",
            "spring.datasource.url=jdbc:h2:mem:categories;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password="
        })
class StoreCategoryServiceTest {
    private static final String STORE = "store-1";
    private static final String OWNER = "owner-1";

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CategoryRedirectRepository redirectRepository;
    @Autowired private TagRepository tagRepository;

    private StoreCategoryService service;

    @BeforeEach
    void setUp() {
        service = new StoreCategoryService(categoryRepository, redirectRepository, new StoreTagService(tagRepository));
    }

    private CategoryRequest cat(String name, String slug, String parentPublicId) {
        return new CategoryRequest(name, slug, null, null, parentPublicId, null, null, null, List.of(), List.of());
    }

    @Test
    void nameIsRequired() {
        assertThatThrownBy(() -> service.create(STORE, OWNER, cat(null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("name");
    }

    @Test
    void slugIsUniquePerStoreWithSuffix() {
        CategoryData first = service.create(STORE, OWNER, cat("Shoes", null, null));
        CategoryData second = service.create(STORE, OWNER, cat("Shoes", null, null));
        assertThat(first.slug()).isEqualTo("shoes");
        assertThat(second.slug()).isEqualTo("shoes-2");
    }

    @Test
    void redirectRecordedWhenSlugChanges() {
        CategoryData created = service.create(STORE, OWNER, cat("Shoes", null, null));
        service.update(STORE, created.publicCategoryId(), cat("Shoes", "sneakers", null));

        List<CategoryRedirectData> redirects = service.redirects(STORE);
        assertThat(redirects).hasSize(1);
        assertThat(redirects.get(0).fromSlug()).isEqualTo("shoes");
        assertThat(redirects.get(0).toSlug()).isEqualTo("sneakers");
    }

    @Test
    void childrenPromotedToGrandparentOnDelete() {
        CategoryData parent = service.create(STORE, OWNER, cat("Ethnic Wear", null, null));
        CategoryData child = service.create(STORE, OWNER, cat("Kurtas", null, parent.publicCategoryId()));
        assertThat(child.parentPublicId()).isEqualTo(parent.publicCategoryId());

        service.delete(STORE, parent.publicCategoryId());

        CategoryData reloadedChild = service.get(STORE, child.publicCategoryId());
        // The parent was a root, so its child is promoted to root (null parent).
        assertThat(reloadedChild.parentPublicId()).isNull();
    }

    @Test
    void childLinksToParentOnCreate() {
        CategoryData parent = service.create(STORE, OWNER, cat("Ethnic Wear", null, null));
        CategoryData child = service.create(STORE, OWNER, cat("Kurtas", null, parent.publicCategoryId()));
        assertThat(child.parentPublicId()).isEqualTo(parent.publicCategoryId());
    }

    @Test
    void unknownParentIsIgnored() {
        CategoryData created = service.create(STORE, OWNER, cat("Kurtas", null, "does-not-exist"));
        assertThat(created.parentPublicId()).isNull();
    }
}
