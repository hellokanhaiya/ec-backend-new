package com.ecommerce.order;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StoreOrderSettingsService {
    private final OrderSettingsRepository settingsRepository;
    private final OrderNumberSequenceRepository sequenceRepository;

    public StoreOrderSettingsService(
            OrderSettingsRepository settingsRepository, OrderNumberSequenceRepository sequenceRepository) {
        this.settingsRepository = settingsRepository;
        this.sequenceRepository = sequenceRepository;
    }

    public OrderSettingsData get(String storeId) {
        OrderSettings settings = settingsRepository.findByStoreId(storeId).orElseGet(() -> {
            OrderSettings defaults = new OrderSettings();
            defaults.setStoreId(storeId);
            return defaults;
        });
        return toData(settings);
    }

    public OrderSettingsData save(String storeId, String ownerPublicUserId, OrderSettingsRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        OrderSettings settings = settingsRepository.findByStoreId(storeId).orElseGet(() -> {
            OrderSettings s = new OrderSettings();
            s.setStoreId(storeId);
            s.setOwnerPublicUserId(ownerPublicUserId);
            return s;
        });

        if (request.orderPrefix() != null && !request.orderPrefix().isBlank()) {
            settings.setOrderPrefix(request.orderPrefix().trim());
        }
        if (request.orderNumberPadding() != null && request.orderNumberPadding() > 0) {
            settings.setOrderNumberPadding(request.orderNumberPadding());
        }
        if (request.financialYearReset() != null) {
            settings.setFinancialYearReset(request.financialYearReset());
        }
        if (request.includeFinancialYear() != null) {
            settings.setIncludeFinancialYear(request.includeFinancialYear());
        }
        if (request.financialYearStartMonth() != null) {
            int month = request.financialYearStartMonth();
            if (month < 1 || month > 12) {
                throw new ResponseStatusException(BAD_REQUEST, "Financial year start month must be between 1 and 12");
            }
            settings.setFinancialYearStartMonth(month);
        }
        if (request.defaultShippingCharge() != null) {
            settings.setDefaultShippingCharge(nonNegative(request.defaultShippingCharge(), "Default shipping charge"));
        }
        if (request.defaultPackageCharge() != null) {
            settings.setDefaultPackageCharge(nonNegative(request.defaultPackageCharge(), "Default package charge"));
        }
        if (request.freeShippingThreshold() != null) {
            settings.setFreeShippingThreshold(nonNegative(request.freeShippingThreshold(), "Free shipping threshold"));
        }
        if (request.defaultTaxRate() != null) {
            settings.setDefaultTaxRate(nonNegative(request.defaultTaxRate(), "Default tax rate"));
        }

        return toData(settingsRepository.save(settings));
    }

    public OrderSettings getRaw(String storeId) {
        return settingsRepository.findByStoreId(storeId).orElseGet(() -> {
            OrderSettings defaults = new OrderSettings();
            defaults.setStoreId(storeId);
            return defaults;
        });
    }

    private OrderSettingsData toData(OrderSettings s) {
        return new OrderSettingsData(
                s.getStoreId(),
                s.getOrderPrefix(),
                s.getOrderNumberPadding(),
                s.isFinancialYearReset(),
                s.isIncludeFinancialYear(),
                s.getFinancialYearStartMonth(),
                s.getDefaultShippingCharge(),
                s.getDefaultPackageCharge(),
                s.getFreeShippingThreshold(),
                s.getDefaultTaxRate(),
                nextOrderNumberPreview(s));
    }

    /** What the next created order would be numbered, without consuming the counter. */
    private String nextOrderNumberPreview(OrderSettings s) {
        Instant now = Instant.now();
        String periodKey = OrderNumberFormatter.periodKey(s, now);
        long lastValue = s.getStoreId() == null
                ? 0L
                : sequenceRepository
                        .findByStoreIdAndPeriodKey(s.getStoreId(), periodKey)
                        .map(OrderNumberSequence::getLastValue)
                        .orElse(0L);
        return OrderNumberFormatter.format(s, now, lastValue + 1);
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.signum() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, field + " cannot be negative");
        }
        return value;
    }
}
