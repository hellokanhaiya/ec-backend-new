package com.ecommerce.shipping;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.ecommerce.store.StoreProfile;
import com.ecommerce.store.StoreProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Advanced shipping: profiles → zones (regions) → rates (tiers), plus manual pincode
 * serviceability and a {@link #quote} engine. A store is seeded with a default
 * "General profile" and a domestic zone on first read. Rate pricing supports flat,
 * weight-, price- and distance-based methods; distance uses the serviceable pincode range's
 * {@code distanceKm}, falling back to the warehouse↔? straight line when coordinates exist.
 */
@Service
@Transactional
public class StoreShippingService {
    private final ShippingProfileRepository profileRepository;
    private final ShippingZoneRepository zoneRepository;
    private final ShippingRateRepository rateRepository;
    private final PincodeRangeRepository pincodeRepository;
    private final DeliverySettingsRepository deliverySettingsRepository;
    private final StoreProfileRepository storeProfileRepository;

    public StoreShippingService(
            ShippingProfileRepository profileRepository,
            ShippingZoneRepository zoneRepository,
            ShippingRateRepository rateRepository,
            PincodeRangeRepository pincodeRepository,
            DeliverySettingsRepository deliverySettingsRepository,
            StoreProfileRepository storeProfileRepository) {
        this.profileRepository = profileRepository;
        this.zoneRepository = zoneRepository;
        this.rateRepository = rateRepository;
        this.pincodeRepository = pincodeRepository;
        this.deliverySettingsRepository = deliverySettingsRepository;
        this.storeProfileRepository = storeProfileRepository;
    }

    // --- delivery settings (dispatch time, COD, free-shipping threshold) ----

    public DeliverySettingsData getDeliverySettings(String storeId) {
        return toSettingsData(loadOrDefaultSettings(storeId));
    }

    public DeliverySettingsData saveDeliverySettings(String storeId, DeliverySettingsRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        DeliverySettings settings = deliverySettingsRepository
                .findByStoreId(storeId)
                .orElseGet(() -> {
                    DeliverySettings fresh = new DeliverySettings();
                    fresh.setStoreId(storeId);
                    return fresh;
                });
        if (request.processingDays() != null) {
            settings.setProcessingDays(Math.max(0, request.processingDays()));
        }
        settings.setOriginPincode(normalize(request.originPincode()));
        settings.setFreeShippingThreshold(request.freeShippingThreshold());
        settings.setCodEnabled(request.codEnabled() == null || request.codEnabled());
        settings.setCodFeeType(CodFeeType.from(request.codFeeType()));
        settings.setCodFeeValue(request.codFeeValue() != null ? request.codFeeValue() : BigDecimal.ZERO);
        settings.setCodMinOrder(request.codMinOrder());
        settings.setCodMaxOrder(request.codMaxOrder());
        return toSettingsData(deliverySettingsRepository.save(settings));
    }

    private DeliverySettings loadOrDefaultSettings(String storeId) {
        return deliverySettingsRepository.findByStoreId(storeId).orElseGet(() -> {
            DeliverySettings defaults = new DeliverySettings();
            defaults.setStoreId(storeId);
            return defaults;
        });
    }

    private static DeliverySettingsData toSettingsData(DeliverySettings s) {
        return new DeliverySettingsData(
                s.getProcessingDays(),
                s.getOriginPincode(),
                s.getFreeShippingThreshold(),
                s.isCodEnabled(),
                s.getCodFeeType().name(),
                s.getCodFeeValue(),
                s.getCodMinOrder(),
                s.getCodMaxOrder());
    }

    // --- seeding -----------------------------------------------------------

    public ShippingProfile ensureSeeded(String storeId, String ownerPublicUserId) {
        return profileRepository
                .findFirstByStoreIdAndDefaultProfileTrue(storeId)
                .orElseGet(() -> {
                    if (profileRepository.countByStoreId(storeId) > 0) {
                        ShippingProfile first = profileRepository
                                .findByStoreIdOrderByCreatedAtAsc(storeId)
                                .get(0);
                        first.setDefaultProfile(true);
                        return profileRepository.save(first);
                    }
                    ShippingProfile profile = new ShippingProfile();
                    profile.setStoreId(storeId);
                    profile.setOwnerPublicUserId(ownerPublicUserId);
                    profile.setName("General profile");
                    profile.setDefaultProfile(true);
                    ShippingProfile saved = profileRepository.save(profile);
                    seedDomesticZone(storeId, ownerPublicUserId, saved.getPublicProfileId());
                    return saved;
                });
    }

    private void seedDomesticZone(String storeId, String ownerPublicUserId, String profilePublicId) {
        StoreProfile store = storeProfileRepository.findByStoreId(storeId).orElse(null);
        String country = store != null && store.getCountryCode() != null ? store.getCountryCode() : "IN";
        String currency = store != null && store.getCurrencyCode() != null ? store.getCurrencyCode() : "INR";
        ShippingZone zone = new ShippingZone();
        zone.setStoreId(storeId);
        zone.setOwnerPublicUserId(ownerPublicUserId);
        zone.setProfilePublicId(profilePublicId);
        zone.setName("Domestic");
        zone.setCurrencyCode(currency);
        ShippingZoneRegion region = new ShippingZoneRegion();
        region.setCountry(country);
        zone.getRegions().add(region);
        zoneRepository.save(zone);
    }

    // --- aggregate settings tree -------------------------------------------

    /** The whole profiles → zones → rates tree the settings page renders. */
    public List<ShippingProfileData> settings(String storeId, String ownerPublicUserId) {
        ensureSeeded(storeId, ownerPublicUserId);
        List<ShippingProfileData> result = new ArrayList<>();
        for (ShippingProfile profile : profileRepository.findByStoreIdOrderByCreatedAtAsc(storeId)) {
            result.add(toProfileData(storeId, profile));
        }
        return result;
    }

    // --- quick setup (simple whole-country delivery) -----------------------

    /**
     * One-call setup for stores that deliver across their whole country. Configures the default
     * zone's regions, rates and a broad serviceable pincode range, then returns the full settings.
     */
    public List<ShippingProfileData> applyQuickSetup(
            String storeId, String ownerPublicUserId, ShippingQuickSetupRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        ShippingProfile profile = ensureSeeded(storeId, ownerPublicUserId);
        StoreProfile store = storeProfileRepository.findByStoreId(storeId).orElse(null);
        String country = store != null && store.getCountryCode() != null ? store.getCountryCode() : "IN";
        String currency = store != null && store.getCurrencyCode() != null ? store.getCurrencyCode() : "INR";

        // Default zone = first zone of the default profile, or a fresh "Domestic" zone.
        ShippingZone zone = zoneRepository
                .findByStoreIdAndProfilePublicIdOrderByCreatedAtAsc(storeId, profile.getPublicProfileId())
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    ShippingZone z = new ShippingZone();
                    z.setStoreId(storeId);
                    z.setOwnerPublicUserId(ownerPublicUserId);
                    z.setProfilePublicId(profile.getPublicProfileId());
                    z.setName("Domestic");
                    return z;
                });
        zone.setCurrencyCode(currency);
        if (zone.getRegions().isEmpty()) {
            ShippingZoneRegion region = new ShippingZoneRegion();
            region.setCountry(country);
            zone.getRegions().add(region);
        }
        zone = zoneRepository.save(zone);

        // Replace the zone's rates with the simple configuration.
        String mode = request.mode() == null ? "FLAT" : request.mode().trim().toUpperCase(java.util.Locale.ROOT);
        rateRepository.deleteByStoreIdAndZonePublicId(storeId, zone.getPublicZoneId());
        BigDecimal charge = nz(request.charge());
        BigDecimal freeAbove = request.freeAbove();
        Integer etaMin = request.etaMinDays();
        Integer etaMax = request.etaMaxDays();

        switch (mode) {
            case "FREE" -> saveSimpleRate(storeId, ownerPublicUserId, zone, "Free delivery", RateType.FREE, BigDecimal.ZERO, null, null, null, etaMin, etaMax);
            case "DISTANCE" -> saveSimpleRate(storeId, ownerPublicUserId, zone, "Distance based", RateType.DISTANCE_BASED, charge, nz(request.perKm()), null, null, etaMin, etaMax);
            default -> {
                // FLAT (with optional free-above threshold)
                BigDecimal maxSubtotal = freeAbove != null && freeAbove.signum() > 0 ? freeAbove : null;
                saveSimpleRate(storeId, ownerPublicUserId, zone, "Standard delivery", RateType.FLAT, charge, null, null, maxSubtotal, etaMin, etaMax);
                if (maxSubtotal != null) {
                    saveSimpleRate(storeId, ownerPublicUserId, zone, "Free delivery", RateType.FREE, BigDecimal.ZERO, null, maxSubtotal, null, etaMin, etaMax);
                }
            }
        }

        // Ensure the whole country is serviceable out of the box.
        ensureBroadPincodeRange(storeId, ownerPublicUserId, zone.getPublicZoneId(), Boolean.TRUE.equals(request.codAvailable()), etaMin, etaMax);

        return settings(storeId, ownerPublicUserId);
    }

    private void saveSimpleRate(
            String storeId,
            String ownerPublicUserId,
            ShippingZone zone,
            String name,
            RateType type,
            BigDecimal basePrice,
            BigDecimal perUnitPrice,
            BigDecimal minSubtotal,
            BigDecimal maxSubtotal,
            Integer etaMin,
            Integer etaMax) {
        ShippingRate rate = new ShippingRate();
        rate.setStoreId(storeId);
        rate.setOwnerPublicUserId(ownerPublicUserId);
        rate.setZonePublicId(zone.getPublicZoneId());
        rate.setName(name);
        rate.setRateType(type);
        rate.setBasePrice(basePrice);
        rate.setPerUnitPrice(perUnitPrice);
        rate.setMinSubtotal(minSubtotal);
        rate.setMaxSubtotal(maxSubtotal);
        rate.setEtaMinDays(etaMin);
        rate.setEtaMaxDays(etaMax);
        rate.setActive(true);
        rateRepository.save(rate);
    }

    private void ensureBroadPincodeRange(
            String storeId, String ownerPublicUserId, String zonePublicId, boolean cod, Integer etaMin, Integer etaMax) {
        boolean hasAny = !pincodeRepository
                .findByStoreIdAndZonePublicIdOrderByCreatedAtAsc(storeId, zonePublicId)
                .isEmpty();
        if (hasAny) {
            return;
        }
        PincodeRange range = new PincodeRange();
        range.setStoreId(storeId);
        range.setOwnerPublicUserId(ownerPublicUserId);
        range.setZonePublicId(zonePublicId);
        range.setFromPincode("000000");
        range.setToPincode("999999");
        range.setCodAvailable(cod);
        range.setEtaMinDays(etaMin);
        range.setEtaMaxDays(etaMax);
        pincodeRepository.save(range);
    }

    // --- profiles ----------------------------------------------------------

    public ShippingProfileData createProfile(String storeId, String ownerPublicUserId, ShippingProfileRequest request) {
        ensureSeeded(storeId, ownerPublicUserId);
        requireName(request == null ? null : request.name());
        ShippingProfile profile = new ShippingProfile();
        profile.setStoreId(storeId);
        profile.setOwnerPublicUserId(ownerPublicUserId);
        profile.setName(request.name().trim());
        profile.setDefaultProfile(false);
        return toProfileData(storeId, profileRepository.save(profile));
    }

    public ShippingProfileData updateProfile(String storeId, String publicProfileId, ShippingProfileRequest request) {
        requireName(request == null ? null : request.name());
        ShippingProfile profile = requireProfile(storeId, publicProfileId);
        profile.setName(request.name().trim());
        return toProfileData(storeId, profileRepository.save(profile));
    }

    public void deleteProfile(String storeId, String publicProfileId) {
        ShippingProfile profile = requireProfile(storeId, publicProfileId);
        if (profile.isDefaultProfile()) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot delete the default shipping profile");
        }
        // Cascade: delete the profile's zones and their rates/pincodes.
        for (ShippingZone zone : zoneRepository.findByStoreIdAndProfilePublicIdOrderByCreatedAtAsc(storeId, publicProfileId)) {
            deleteZoneCascade(storeId, zone);
        }
        profileRepository.delete(profile);
    }

    // --- zones -------------------------------------------------------------

    public ShippingZoneData createZone(String storeId, String ownerPublicUserId, ShippingZoneRequest request) {
        ensureSeeded(storeId, ownerPublicUserId);
        requireName(request == null ? null : request.name());
        String profilePublicId = request.profilePublicId() != null && !request.profilePublicId().isBlank()
                ? requireProfile(storeId, request.profilePublicId()).getPublicProfileId()
                : ensureSeeded(storeId, ownerPublicUserId).getPublicProfileId();
        ShippingZone zone = new ShippingZone();
        zone.setStoreId(storeId);
        zone.setOwnerPublicUserId(ownerPublicUserId);
        zone.setProfilePublicId(profilePublicId);
        applyZone(zone, request);
        return toZoneData(storeId, zoneRepository.save(zone));
    }

    public ShippingZoneData updateZone(String storeId, String publicZoneId, ShippingZoneRequest request) {
        requireName(request == null ? null : request.name());
        ShippingZone zone = requireZone(storeId, publicZoneId);
        applyZone(zone, request);
        return toZoneData(storeId, zoneRepository.save(zone));
    }

    public void deleteZone(String storeId, String publicZoneId) {
        deleteZoneCascade(storeId, requireZone(storeId, publicZoneId));
    }

    private void deleteZoneCascade(String storeId, ShippingZone zone) {
        rateRepository.deleteByStoreIdAndZonePublicId(storeId, zone.getPublicZoneId());
        pincodeRepository.deleteByStoreIdAndZonePublicId(storeId, zone.getPublicZoneId());
        zoneRepository.delete(zone);
    }

    private void applyZone(ShippingZone zone, ShippingZoneRequest request) {
        zone.setName(request.name().trim());
        zone.setCurrencyCode(normalize(request.currencyCode()));
        zone.getRegions().clear();
        if (request.regions() != null) {
            for (ShippingRegionRequest r : request.regions()) {
                if (r == null || isBlank(r.country())) {
                    continue;
                }
                ShippingZoneRegion region = new ShippingZoneRegion();
                region.setCountry(r.country().trim().toUpperCase(java.util.Locale.ROOT));
                region.setState(normalize(r.state()));
                region.setCity(normalize(r.city()));
                zone.getRegions().add(region);
            }
        }
        if (zone.getRegions().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "A zone needs at least one region (country)");
        }
    }

    // --- rates -------------------------------------------------------------

    public ShippingRateData createRate(String storeId, String ownerPublicUserId, ShippingRateRequest request) {
        if (request == null || isBlank(request.zonePublicId())) {
            throw new ResponseStatusException(BAD_REQUEST, "zonePublicId is required");
        }
        requireName(request.name());
        requireZone(storeId, request.zonePublicId());
        ShippingRate rate = new ShippingRate();
        rate.setStoreId(storeId);
        rate.setOwnerPublicUserId(ownerPublicUserId);
        rate.setZonePublicId(request.zonePublicId());
        applyRate(rate, request);
        return toRateData(rateRepository.save(rate));
    }

    public ShippingRateData updateRate(String storeId, String publicRateId, ShippingRateRequest request) {
        requireName(request == null ? null : request.name());
        ShippingRate rate = requireRate(storeId, publicRateId);
        applyRate(rate, request);
        return toRateData(rateRepository.save(rate));
    }

    public void deleteRate(String storeId, String publicRateId) {
        rateRepository.delete(requireRate(storeId, publicRateId));
    }

    private void applyRate(ShippingRate rate, ShippingRateRequest request) {
        rate.setName(request.name().trim());
        rate.setRateType(RateType.from(request.rateType()));
        rate.setBasePrice(request.basePrice() != null ? request.basePrice() : BigDecimal.ZERO);
        rate.setPerUnitPrice(request.perUnitPrice());
        rate.setMinWeight(request.minWeight());
        rate.setMaxWeight(request.maxWeight());
        rate.setMinSubtotal(request.minSubtotal());
        rate.setMaxSubtotal(request.maxSubtotal());
        rate.setEtaMinDays(request.etaMinDays());
        rate.setEtaMaxDays(request.etaMaxDays());
        rate.setActive(request.active() == null || request.active());
        rate.getTiers().clear();
        if (request.tiers() != null) {
            for (ShippingRateTierRequest t : request.tiers()) {
                if (t == null) {
                    continue;
                }
                ShippingRateTier tier = new ShippingRateTier();
                tier.setLowerBound(t.lowerBound());
                tier.setUpperBound(t.upperBound());
                tier.setPrice(t.price() != null ? t.price() : BigDecimal.ZERO);
                tier.setPerUnitPrice(t.perUnitPrice());
                rate.getTiers().add(tier);
            }
        }
    }

    // --- pincode serviceability --------------------------------------------

    public List<PincodeRangeData> listPincodeRanges(String storeId, String ownerPublicUserId, String zonePublicId) {
        ensureSeeded(storeId, ownerPublicUserId);
        List<PincodeRange> ranges = (zonePublicId == null || zonePublicId.isBlank())
                ? pincodeRepository.findByStoreIdOrderByCreatedAtAsc(storeId)
                : pincodeRepository.findByStoreIdAndZonePublicIdOrderByCreatedAtAsc(storeId, zonePublicId);
        return ranges.stream().map(StoreShippingService::toPincodeData).toList();
    }

    public PincodeRangeData createPincodeRange(String storeId, String ownerPublicUserId, PincodeRangeRequest request) {
        if (request == null || isBlank(request.zonePublicId())) {
            throw new ResponseStatusException(BAD_REQUEST, "zonePublicId is required");
        }
        requireZone(storeId, request.zonePublicId());
        PincodeRange range = new PincodeRange();
        range.setStoreId(storeId);
        range.setOwnerPublicUserId(ownerPublicUserId);
        applyPincode(range, request);
        return toPincodeData(pincodeRepository.save(range));
    }

    public PincodeRangeData updatePincodeRange(String storeId, String publicPincodeId, PincodeRangeRequest request) {
        PincodeRange range = requirePincode(storeId, publicPincodeId);
        if (request != null && !isBlank(request.zonePublicId())) {
            requireZone(storeId, request.zonePublicId());
        }
        applyPincode(range, request);
        return toPincodeData(pincodeRepository.save(range));
    }

    public void deletePincodeRange(String storeId, String publicPincodeId) {
        pincodeRepository.delete(requirePincode(storeId, publicPincodeId));
    }

    private void applyPincode(PincodeRange range, PincodeRangeRequest request) {
        if (request == null || isBlank(request.fromPincode())) {
            throw new ResponseStatusException(BAD_REQUEST, "fromPincode is required");
        }
        String from = request.fromPincode().trim();
        String to = isBlank(request.toPincode()) ? from : request.toPincode().trim();
        if (parsePincode(from) > parsePincode(to)) {
            throw new ResponseStatusException(BAD_REQUEST, "fromPincode must be <= toPincode");
        }
        if (!isBlank(request.zonePublicId())) {
            range.setZonePublicId(request.zonePublicId());
        }
        range.setWarehousePublicId(normalize(request.warehousePublicId()));
        range.setFromPincode(from);
        range.setToPincode(to);
        range.setCodAvailable(Boolean.TRUE.equals(request.codAvailable()));
        range.setEtaMinDays(request.etaMinDays());
        range.setEtaMaxDays(request.etaMaxDays());
        range.setDistanceKm(request.distanceKm());
    }

    /** First pincode range covering {@code pincode}, if any. */
    public Optional<PincodeRange> matchPincode(String storeId, String pincode) {
        if (pincode == null || pincode.isBlank()) {
            return Optional.empty();
        }
        long value = parsePincode(pincode.trim());
        return pincodeRepository.findByStoreIdOrderByCreatedAtAsc(storeId).stream()
                .filter(r -> value >= parsePincode(r.getFromPincode()) && value <= parsePincode(r.getToPincode()))
                .findFirst();
    }

    // --- quote -------------------------------------------------------------

    public ShippingQuoteData quote(String storeId, String ownerPublicUserId, ShippingQuoteRequest request) {
        ensureSeeded(storeId, ownerPublicUserId);
        if (request == null) {
            return new ShippingQuoteData(false, List.of(), false, null);
        }
        Optional<PincodeRange> match = matchPincode(storeId, request.pincode());
        if (match.isEmpty()) {
            return new ShippingQuoteData(false, List.of(), false, null);
        }
        PincodeRange range = match.get();
        ShippingZone zone = zoneRepository.findByStoreIdAndPublicZoneId(storeId, range.getZonePublicId()).orElse(null);
        if (zone == null) {
            return new ShippingQuoteData(false, List.of(), false, null);
        }
        BigDecimal weight = request.cartWeight() != null ? request.cartWeight() : BigDecimal.ZERO;
        BigDecimal subtotal = request.cartSubtotal() != null ? request.cartSubtotal() : BigDecimal.ZERO;
        // Distance-based rates use the serviceable pincode range's manually-set distanceKm.
        // (A courier provider or a pincode→geo dataset can populate this later.)
        BigDecimal distance = range.getDistanceKm() != null ? range.getDistanceKm() : BigDecimal.ZERO;

        // Delivery settings gate: dispatch time feeds the shown ETA; COD is resolved separately.
        DeliverySettings settings = loadOrDefaultSettings(storeId);
        int processingDays = settings.getProcessingDays();
        boolean codAvailable = range.isCodAvailable()
                && settings.isCodEnabled()
                && withinCodLimits(subtotal, settings.getCodMinOrder(), settings.getCodMaxOrder());
        BigDecimal codFee = codAvailable ? computeCodFee(settings, subtotal) : null;

        List<ShippingQuoteOptionData> options = new ArrayList<>();
        for (ShippingRate rate : rateRepository.findByStoreIdAndZonePublicIdOrderByCreatedAtAsc(storeId, zone.getPublicZoneId())) {
            if (!rate.isActive() || !conditionsMatch(rate, weight, subtotal)) {
                continue;
            }
            BigDecimal price = computePrice(rate, weight, subtotal, distance);
            Integer transitMin = rate.getEtaMinDays() != null ? rate.getEtaMinDays() : range.getEtaMinDays();
            Integer transitMax = rate.getEtaMaxDays() != null ? rate.getEtaMaxDays() : range.getEtaMaxDays();
            options.add(new ShippingQuoteOptionData(
                    rate.getPublicRateId(),
                    zone.getPublicZoneId(),
                    rate.getName(),
                    rate.getRateType().name(),
                    price,
                    zone.getCurrencyCode(),
                    transitMin != null ? transitMin + processingDays : null,
                    transitMax != null ? transitMax + processingDays : null,
                    codAvailable));
        }
        return new ShippingQuoteData(true, options, codAvailable, codFee);
    }

    private static boolean withinCodLimits(BigDecimal subtotal, BigDecimal min, BigDecimal max) {
        if (min != null && subtotal.compareTo(min) < 0) {
            return false;
        }
        return max == null || subtotal.compareTo(max) <= 0;
    }

    private static BigDecimal computeCodFee(DeliverySettings settings, BigDecimal subtotal) {
        BigDecimal value = settings.getCodFeeValue() != null ? settings.getCodFeeValue() : BigDecimal.ZERO;
        BigDecimal fee = settings.getCodFeeType() == CodFeeType.PERCENT
                ? subtotal.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : value;
        return fee.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean conditionsMatch(ShippingRate rate, BigDecimal weight, BigDecimal subtotal) {
        if (rate.getMinWeight() != null && weight.compareTo(rate.getMinWeight()) < 0) {
            return false;
        }
        if (rate.getMaxWeight() != null && weight.compareTo(rate.getMaxWeight()) > 0) {
            return false;
        }
        if (rate.getMinSubtotal() != null && subtotal.compareTo(rate.getMinSubtotal()) < 0) {
            return false;
        }
        return rate.getMaxSubtotal() == null || subtotal.compareTo(rate.getMaxSubtotal()) <= 0;
    }

    private BigDecimal computePrice(ShippingRate rate, BigDecimal weight, BigDecimal subtotal, BigDecimal distance) {
        BigDecimal price =
                switch (rate.getRateType()) {
                    case FREE -> BigDecimal.ZERO;
                    case FLAT -> nz(rate.getBasePrice());
                    case WEIGHT_BASED -> tierOrLinear(rate, weight);
                    case PRICE_BASED -> tierOrLinear(rate, subtotal);
                    case DISTANCE_BASED -> tierOrLinear(rate, distance);
                };
        return price.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** Use a matching tier band if defined, else {@code base + perUnit × quantity}. */
    private BigDecimal tierOrLinear(ShippingRate rate, BigDecimal quantity) {
        BigDecimal qty = quantity != null ? quantity : BigDecimal.ZERO;
        for (ShippingRateTier tier : rate.getTiers()) {
            BigDecimal lower = tier.getLowerBound() != null ? tier.getLowerBound() : BigDecimal.ZERO;
            boolean aboveLower = qty.compareTo(lower) >= 0;
            boolean belowUpper = tier.getUpperBound() == null || qty.compareTo(tier.getUpperBound()) < 0;
            if (aboveLower && belowUpper) {
                return nz(tier.getPrice()).add(nz(tier.getPerUnitPrice()).multiply(qty));
            }
        }
        return nz(rate.getBasePrice()).add(nz(rate.getPerUnitPrice()).multiply(qty));
    }

    // --- mapping -----------------------------------------------------------

    private ShippingProfileData toProfileData(String storeId, ShippingProfile profile) {
        List<ShippingZoneData> zones = zoneRepository
                .findByStoreIdAndProfilePublicIdOrderByCreatedAtAsc(storeId, profile.getPublicProfileId())
                .stream()
                .map(zone -> toZoneData(storeId, zone))
                .toList();
        return new ShippingProfileData(
                profile.getPublicProfileId(),
                profile.getName(),
                profile.isDefaultProfile(),
                zones,
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    private ShippingZoneData toZoneData(String storeId, ShippingZone zone) {
        List<ShippingRegionData> regions = zone.getRegions().stream()
                .map(r -> new ShippingRegionData(r.getId(), r.getCountry(), r.getState(), r.getCity()))
                .toList();
        List<ShippingRateData> rates = rateRepository
                .findByStoreIdAndZonePublicIdOrderByCreatedAtAsc(storeId, zone.getPublicZoneId())
                .stream()
                .map(StoreShippingService::toRateData)
                .toList();
        return new ShippingZoneData(
                zone.getPublicZoneId(),
                zone.getProfilePublicId(),
                zone.getName(),
                zone.getCurrencyCode(),
                regions,
                rates,
                zone.getCreatedAt(),
                zone.getUpdatedAt());
    }

    private static ShippingRateData toRateData(ShippingRate rate) {
        List<ShippingRateTierData> tiers = rate.getTiers().stream()
                .map(t -> new ShippingRateTierData(
                        t.getId(), t.getLowerBound(), t.getUpperBound(), t.getPrice(), t.getPerUnitPrice()))
                .toList();
        return new ShippingRateData(
                rate.getPublicRateId(),
                rate.getZonePublicId(),
                rate.getName(),
                rate.getRateType().name(),
                rate.getBasePrice(),
                rate.getPerUnitPrice(),
                rate.getMinWeight(),
                rate.getMaxWeight(),
                rate.getMinSubtotal(),
                rate.getMaxSubtotal(),
                rate.getEtaMinDays(),
                rate.getEtaMaxDays(),
                rate.isActive(),
                tiers,
                rate.getCreatedAt(),
                rate.getUpdatedAt());
    }

    private static PincodeRangeData toPincodeData(PincodeRange range) {
        return new PincodeRangeData(
                range.getPublicPincodeId(),
                range.getZonePublicId(),
                range.getWarehousePublicId(),
                range.getFromPincode(),
                range.getToPincode(),
                range.isCodAvailable(),
                range.getEtaMinDays(),
                range.getEtaMaxDays(),
                range.getDistanceKm(),
                range.getCreatedAt(),
                range.getUpdatedAt());
    }

    // --- lookups & utils ---------------------------------------------------

    private ShippingProfile requireProfile(String storeId, String publicProfileId) {
        return profileRepository
                .findByStoreIdAndPublicProfileId(storeId, publicProfileId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shipping profile not found"));
    }

    private ShippingZone requireZone(String storeId, String publicZoneId) {
        return zoneRepository
                .findByStoreIdAndPublicZoneId(storeId, publicZoneId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shipping zone not found"));
    }

    private ShippingRate requireRate(String storeId, String publicRateId) {
        return rateRepository
                .findByStoreIdAndPublicRateId(storeId, publicRateId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shipping rate not found"));
    }

    private PincodeRange requirePincode(String storeId, String publicPincodeId) {
        return pincodeRepository
                .findByStoreIdAndPublicPincodeId(storeId, publicPincodeId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pincode range not found"));
    }

    private static void requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Name is required");
        }
    }

    /** Parse a pincode to a comparable long; non-numeric parts are stripped. */
    private static long parsePincode(String value) {
        if (value == null) {
            return 0L;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
