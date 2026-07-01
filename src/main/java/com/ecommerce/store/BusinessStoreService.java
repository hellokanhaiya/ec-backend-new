package com.ecommerce.store;

import com.ecommerce.auth.AuthAudience;
import com.ecommerce.auth.BusinessCategoryOption;
import com.ecommerce.auth.BusinessStoreData;
import com.ecommerce.auth.BusinessStoreRequest;
import com.ecommerce.auth.repository.AdminAuthUserRepository;
import com.ecommerce.auth.repository.ConsumerAuthUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional
public class BusinessStoreService {
    private static final List<BusinessCategoryOption> CATEGORIES = List.of(
            new BusinessCategoryOption("GROCERY", "Grocery", false),
            new BusinessCategoryOption("PHARMACY", "Pharmacy", false),
            new BusinessCategoryOption("FASHION", "Fashion", false),
            new BusinessCategoryOption("ELECTRONICS", "Electronics", false),
            new BusinessCategoryOption("BEAUTY", "Beauty", false),
            new BusinessCategoryOption("HOME_KITCHEN", "Home & Kitchen", false),
            new BusinessCategoryOption("SPORTS", "Sports", false),
            new BusinessCategoryOption("BOOKS", "Books", false),
            new BusinessCategoryOption("OTHER", "Other", true));

    private final AdminAuthUserRepository adminAuthUserRepository;
    private final ConsumerAuthUserRepository consumerAuthUserRepository;
    private final StoreProfileRepository storeProfileRepository;

    public BusinessStoreService(
            AdminAuthUserRepository adminAuthUserRepository,
            ConsumerAuthUserRepository consumerAuthUserRepository,
            StoreProfileRepository storeProfileRepository) {
        this.adminAuthUserRepository = adminAuthUserRepository;
        this.consumerAuthUserRepository = consumerAuthUserRepository;
        this.storeProfileRepository = storeProfileRepository;
    }

    public List<BusinessCategoryOption> listCategories() {
        return CATEGORIES;
    }

    public BusinessStoreData getStoreProfile(AuthAudience audience, String publicUserId) {
        if (publicUserId == null || publicUserId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "publicUserId is required");
        }

        StoreProfile storeProfile = storeProfileRepository.findByOwnerPublicUserId(publicUserId.trim())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Store profile not found"));
        validateAudience(audience, storeProfile.getAudience());
        return toData(storeProfile);
    }

    public BusinessStoreData saveStoreProfile(
            AuthAudience audience,
            Long ownerUserId,
            String ownerPublicUserId,
            BusinessStoreRequest request) {
        validateRequest(request, ownerPublicUserId);

        StoreProfile profile = storeProfileRepository.findByOwnerPublicUserId(ownerPublicUserId).orElseGet(StoreProfile::new);
        boolean creating = profile.getId() == null;
        profile.setOwnerUserId(ownerUserId);
        profile.setOwnerPublicUserId(ownerPublicUserId);
        profile.setAudience(audience);
        profile.setBusinessName(request.businessName().trim());
        profile.setCategoryKey(request.categoryKey().trim().toUpperCase(Locale.ROOT));
        profile.setCategoryLabel(request.categoryLabel().trim());
        profile.setCustomCategory(normalizeOptional(request.customCategory()));
        profile.setCurrencyCode(request.currencyCode().trim().toUpperCase(Locale.ROOT));
        profile.setCountryCode(request.countryCode().trim().toUpperCase(Locale.ROOT));
        profile.setCountryName(request.countryName().trim());
        if (creating) {
            profile.setPublicUserId(ownerPublicUserId);
            profile.setOrgId(UUID.randomUUID().toString());
            profile.setStoreId(UUID.randomUUID().toString());
        }

        StoreProfile savedProfile = storeProfileRepository.save(profile);
        if (creating || isBlank(savedProfile.getOrgId()) || isBlank(savedProfile.getStoreId())) {
            applyReadableIdentifiers(savedProfile);
            savedProfile = storeProfileRepository.save(savedProfile);
        }

        return toData(savedProfile);
    }

    public Optional<BusinessStoreData> findStoreProfile(AuthAudience audience, String publicUserId) {
        if (publicUserId == null || publicUserId.isBlank()) {
            return Optional.empty();
        }

        return storeProfileRepository.findByOwnerPublicUserId(publicUserId.trim())
                .filter(profile -> profile.getAudience() == audience)
                .map(this::toData);
    }

    private void validateRequest(BusinessStoreRequest request, String ownerPublicUserId) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Business profile is required");
        }
        if (ownerPublicUserId == null || ownerPublicUserId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "publicUserId is required");
        }
        if (request.publicUserId() != null && !request.publicUserId().isBlank()
                && !ownerPublicUserId.trim().equals(request.publicUserId().trim())) {
            throw new ResponseStatusException(BAD_REQUEST, "publicUserId mismatch");
        }
        if (request.businessName() == null || request.businessName().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "businessName is required");
        }
        if (request.categoryKey() == null || request.categoryKey().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "categoryKey is required");
        }
        if (request.categoryLabel() == null || request.categoryLabel().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "categoryLabel is required");
        }
        if (request.currencyCode() == null || request.currencyCode().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "currencyCode is required");
        }
        if (request.countryCode() == null || request.countryCode().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "countryCode is required");
        }
        if (request.countryName() == null || request.countryName().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "countryName is required");
        }
        if ("OTHER".equalsIgnoreCase(request.categoryKey())
                && (request.customCategory() == null || request.customCategory().isBlank())) {
            throw new ResponseStatusException(BAD_REQUEST, "customCategory is required when category is Other");
        }
    }

    private void validateAudience(AuthAudience requested, AuthAudience actual) {
        if (requested != actual) {
            throw new ResponseStatusException(BAD_REQUEST, "Audience mismatch");
        }
    }

    private BusinessStoreData toData(StoreProfile profile) {
        return new BusinessStoreData(
                profile.getPublicUserId(),
                profile.getOrgId(),
                profile.getStoreId(),
                profile.getBusinessName(),
                profile.getCategoryKey(),
                profile.getCategoryLabel(),
                profile.getCustomCategory(),
                profile.getCurrencyCode(),
                profile.getCountryCode(),
                profile.getCountryName(),
                profile.getAudience().name());
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void applyReadableIdentifiers(StoreProfile profile) {
        if (profile.getId() == null) {
            throw new IllegalStateException("Store profile id is required to generate readable identifiers");
        }

        String readableNumber = String.format("%05d", 10000 + profile.getId());
        profile.setStoreId(readableNumber);
        profile.setOrgId("ORG-" + readableNumber);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
