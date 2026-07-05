package com.ecommerce.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.store.StoreProfileRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(
        properties = {
            "spring.config.import=",
            "spring.datasource.url=jdbc:h2:mem:shipping;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password="
        })
class StoreShippingServiceTest {
    private static final String STORE = "store-1";
    private static final String OWNER = "owner-1";

    @Autowired private ShippingProfileRepository profileRepository;
    @Autowired private ShippingZoneRepository zoneRepository;
    @Autowired private ShippingRateRepository rateRepository;
    @Autowired private PincodeRangeRepository pincodeRepository;
    @Autowired private DeliverySettingsRepository deliverySettingsRepository;
    @Autowired private StoreProfileRepository storeProfileRepository;

    private StoreShippingService service;

    @BeforeEach
    void setUp() {
        service = new StoreShippingService(
                profileRepository,
                zoneRepository,
                rateRepository,
                pincodeRepository,
                deliverySettingsRepository,
                storeProfileRepository);
    }

    private String seededZoneId() {
        List<ShippingProfileData> settings = service.settings(STORE, OWNER);
        return settings.get(0).zones().get(0).publicZoneId();
    }

    @Test
    void seedsDefaultProfileAndDomesticZone() {
        List<ShippingProfileData> settings = service.settings(STORE, OWNER);
        assertThat(settings).hasSize(1);
        assertThat(settings.get(0).defaultProfile()).isTrue();
        assertThat(settings.get(0).zones()).hasSize(1);
        assertThat(settings.get(0).zones().get(0).name()).isEqualTo("Domestic");
    }

    @Test
    void flatRateQuotedForServiceablePincode() {
        String zone = seededZoneId();
        service.createRate(
                STORE,
                OWNER,
                new ShippingRateRequest(zone, "Standard", "FLAT", new BigDecimal("60"), null, null, null, null, null, 2, 4, true, null));
        service.createPincodeRange(
                STORE, OWNER, new PincodeRangeRequest(zone, null, "560001", "560099", true, 2, 4, null));

        ShippingQuoteData quote = service.quote(
                STORE, OWNER, new ShippingQuoteRequest("IN", null, "Bengaluru", "560050", null, new BigDecimal("999"), null, null));

        assertThat(quote.serviceable()).isTrue();
        assertThat(quote.options()).hasSize(1);
        assertThat(quote.options().get(0).price()).isEqualByComparingTo("60.00");
        assertThat(quote.options().get(0).codAvailable()).isTrue();
        // ETA shown = transit (2) + default dispatch/processing (1 day).
        assertThat(quote.options().get(0).etaMinDays()).isEqualTo(3);
        assertThat(quote.codAvailable()).isTrue();
    }

    @Test
    void codFeeAndLimitsResolveFromDeliverySettings() {
        String zone = seededZoneId();
        service.createRate(
                STORE, OWNER, new ShippingRateRequest(zone, "Standard", "FLAT", new BigDecimal("40"), null, null, null, null, null, null, null, true, null));
        service.createPincodeRange(STORE, OWNER, new PincodeRangeRequest(zone, null, "560001", "560099", true, 2, 4, null));
        // COD: flat ₹30 fee, only for orders ₹200–₹5000.
        service.saveDeliverySettings(
                STORE,
                new DeliverySettingsRequest(2, "560001", null, true, "FLAT", new BigDecimal("30"), new BigDecimal("200"), new BigDecimal("5000")));

        ShippingQuoteData eligible = service.quote(
                STORE, OWNER, new ShippingQuoteRequest("IN", null, null, "560010", null, new BigDecimal("1000"), null, null));
        assertThat(eligible.codAvailable()).isTrue();
        assertThat(eligible.codFee()).isEqualByComparingTo("30.00");
        // ETA = transit 2 + processing 2 = 4.
        assertThat(eligible.options().get(0).etaMinDays()).isEqualTo(4);

        // Below the COD minimum → COD unavailable.
        ShippingQuoteData tooSmall = service.quote(
                STORE, OWNER, new ShippingQuoteRequest("IN", null, null, "560010", null, new BigDecimal("100"), null, null));
        assertThat(tooSmall.codAvailable()).isFalse();
    }

    @Test
    void weightBasedRateComputesBasePlusPerKg() {
        String zone = seededZoneId();
        service.createRate(
                STORE,
                OWNER,
                new ShippingRateRequest(
                        zone, "By weight", "WEIGHT_BASED", new BigDecimal("50"), new BigDecimal("10"), null, null, null, null, null, null, true, null));
        service.createPincodeRange(STORE, OWNER, new PincodeRangeRequest(zone, null, "560001", "560099", false, null, null, null));

        ShippingQuoteData quote = service.quote(
                STORE, OWNER, new ShippingQuoteRequest("IN", null, null, "560010", new BigDecimal("2"), BigDecimal.ZERO, null, null));

        assertThat(quote.options().get(0).price()).isEqualByComparingTo("70.00"); // 50 + 10*2
    }

    @Test
    void distanceBasedRateUsesPincodeRangeDistance() {
        String zone = seededZoneId();
        service.createRate(
                STORE,
                OWNER,
                new ShippingRateRequest(
                        zone, "By distance", "DISTANCE_BASED", new BigDecimal("30"), new BigDecimal("2"), null, null, null, null, null, null, true, null));
        service.createPincodeRange(
                STORE, OWNER, new PincodeRangeRequest(zone, null, "560001", "560099", false, null, null, new BigDecimal("15")));

        ShippingQuoteData quote = service.quote(
                STORE, OWNER, new ShippingQuoteRequest("IN", null, null, "560010", BigDecimal.ZERO, BigDecimal.ZERO, null, null));

        assertThat(quote.options().get(0).price()).isEqualByComparingTo("60.00"); // 30 + 2*15
    }

    @Test
    void nonServiceablePincodeReturnsEmpty() {
        String zone = seededZoneId();
        service.createRate(
                STORE, OWNER, new ShippingRateRequest(zone, "Standard", "FLAT", new BigDecimal("60"), null, null, null, null, null, null, null, true, null));
        service.createPincodeRange(STORE, OWNER, new PincodeRangeRequest(zone, null, "560001", "560099", false, null, null, null));

        ShippingQuoteData quote = service.quote(
                STORE, OWNER, new ShippingQuoteRequest("IN", null, null, "999999", null, null, null, null));

        assertThat(quote.serviceable()).isFalse();
        assertThat(quote.options()).isEmpty();
    }

    @Test
    void subtotalConditionFiltersRate() {
        String zone = seededZoneId();
        // Free over 1000 subtotal only.
        service.createRate(
                STORE,
                OWNER,
                new ShippingRateRequest(
                        zone, "Free over 1000", "FREE", BigDecimal.ZERO, null, null, null, new BigDecimal("1000"), null, null, null, true, null));
        service.createPincodeRange(STORE, OWNER, new PincodeRangeRequest(zone, null, "560001", "560099", false, null, null, null));

        ShippingQuoteData below = service.quote(
                STORE, OWNER, new ShippingQuoteRequest("IN", null, null, "560010", null, new BigDecimal("500"), null, null));
        ShippingQuoteData above = service.quote(
                STORE, OWNER, new ShippingQuoteRequest("IN", null, null, "560010", null, new BigDecimal("1500"), null, null));

        assertThat(below.options()).isEmpty();
        assertThat(above.options()).hasSize(1);
        assertThat(above.options().get(0).price()).isEqualByComparingTo("0.00");
    }

    @Test
    void quickSetupFlatWithFreeAboveMakesCountryServiceable() {
        // No zones/rates configured beyond the seed; quick setup should wire everything.
        service.applyQuickSetup(
                STORE,
                OWNER,
                new ShippingQuickSetupRequest("FLAT", new BigDecimal("60"), new BigDecimal("999"), null, 2, 5, true));

        // Pan-India serviceable out of the box (broad pincode range seeded).
        ShippingQuoteData below = service.quote(
                STORE, OWNER, new ShippingQuoteRequest("IN", null, null, "560001", null, new BigDecimal("500"), null, null));
        assertThat(below.serviceable()).isTrue();
        assertThat(below.options()).extracting(ShippingQuoteOptionData::name).contains("Standard delivery");
        assertThat(below.options().get(0).price()).isEqualByComparingTo("60.00");
        assertThat(below.options().get(0).codAvailable()).isTrue();

        // At/above the free-above threshold, free delivery applies.
        ShippingQuoteData above = service.quote(
                STORE, OWNER, new ShippingQuoteRequest("IN", null, null, "110001", null, new BigDecimal("1500"), null, null));
        assertThat(above.options()).extracting(ShippingQuoteOptionData::name).contains("Free delivery");
    }

    @Test
    void quickSetupIsIdempotentAndReplacesRates() {
        service.applyQuickSetup(STORE, OWNER, new ShippingQuickSetupRequest("FLAT", new BigDecimal("60"), null, null, 2, 5, false));
        service.applyQuickSetup(STORE, OWNER, new ShippingQuickSetupRequest("FREE", null, null, null, 1, 3, false));

        String zone = seededZoneId();
        assertThat(rateRepository.findByStoreIdAndZonePublicIdOrderByCreatedAtAsc(STORE, zone))
                .extracting(r -> r.getRateType().name())
                .containsExactly("FREE");
    }

    @Test
    void cannotDeleteDefaultProfile() {
        String defaultProfileId = service.settings(STORE, OWNER).get(0).publicProfileId();
        assertThatThrownBy(() -> service.deleteProfile(STORE, defaultProfileId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("default");
    }
}
