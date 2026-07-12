package com.ecommerce.theme;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThemeVersionRepository extends JpaRepository<ThemeVersionEntity, String> {
    List<ThemeVersionEntity> findByThemeIdOrderByCreatedAtDesc(String themeId);

    Optional<ThemeVersionEntity> findByIdAndThemeId(String id, String themeId);

    void deleteByThemeId(String themeId);
}
