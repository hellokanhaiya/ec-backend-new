package com.ecommerce.vendor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Store-scoped CRUD + lifecycle for marketplace {@link Vendor}s. Mirrors the
 * house pattern (storeId scoping on every query, public-id lookups, 404 helper)
 * used by StoreOrderService / AbandonedCartService.
 */
@Service
@Transactional
public class VendorService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final VendorRepository repository;

    public VendorService(VendorRepository repository) {
        this.repository = repository;
    }

    // --- Queries ------------------------------------------------------------

    @Transactional
    public VendorListData list(
            String storeId,
            String ownerPublicUserId,
            String search,
            String status,
            int page,
            int size) {
        ensureDemoData(storeId, ownerPublicUserId);

        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        VendorStatus statusFilter =
                (status == null || status.isBlank() || "all".equalsIgnoreCase(status))
                        ? null
                        : VendorStatus.from(status);

        List<Vendor> filtered =
                repository.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                        .filter(v -> statusFilter == null || v.getStatus() == statusFilter)
                        .filter(v -> matchesSearch(v, query))
                        .toList();

        long total = filtered.size();
        List<Vendor> pageItems;
        if (size <= 0) {
            pageItems = filtered;
        } else {
            int from = Math.max(0, (Math.max(page, 1) - 1) * size);
            int to = Math.min(filtered.size(), from + size);
            pageItems = from >= filtered.size() ? List.of() : filtered.subList(from, to);
        }

        return new VendorListData(
                pageItems.stream().map(this::toData).toList(),
                total,
                Math.max(page, 1),
                size);
    }

    @Transactional
    public VendorOverviewData overview(String storeId, String ownerPublicUserId) {
        ensureDemoData(storeId, ownerPublicUserId);
        List<Vendor> vendors = repository.findByStoreIdOrderByCreatedAtDesc(storeId);
        long total = vendors.size();
        long approved = vendors.stream().filter(v -> v.getStatus() == VendorStatus.APPROVED).count();
        long pending = vendors.stream().filter(v -> v.getStatus() == VendorStatus.PENDING).count();
        long suspended = vendors.stream().filter(v -> v.getStatus() == VendorStatus.SUSPENDED).count();
        return new VendorOverviewData(total, approved, pending, suspended);
    }

    @Transactional
    public VendorData get(String storeId, String publicVendorId) {
        return toData(require(storeId, publicVendorId));
    }

    // --- Mutations ----------------------------------------------------------

    @Transactional
    public VendorData create(String storeId, String ownerPublicUserId, VendorRequest request) {
        Vendor vendor = new Vendor();
        vendor.setStoreId(storeId);
        vendor.setOwnerPublicUserId(ownerPublicUserId == null ? storeId : ownerPublicUserId);
        applyRequest(vendor, request);
        return toData(repository.save(vendor));
    }

    @Transactional
    public VendorData update(String storeId, String publicVendorId, VendorRequest request) {
        Vendor vendor = require(storeId, publicVendorId);
        applyRequest(vendor, request);
        return toData(repository.save(vendor));
    }

    @Transactional
    public VendorData setStatus(String storeId, String publicVendorId, VendorStatus status) {
        Vendor vendor = require(storeId, publicVendorId);
        vendor.setStatus(status);
        return toData(repository.save(vendor));
    }

    @Transactional
    public void delete(String storeId, String publicVendorId) {
        Vendor vendor = require(storeId, publicVendorId);
        repository.delete(vendor);
    }

    // --- Helpers ------------------------------------------------------------

    private Vendor require(String storeId, String publicVendorId) {
        return repository
                .findByStoreIdAndPublicVendorId(storeId, publicVendorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
    }

    private void applyRequest(Vendor vendor, VendorRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        String name = normalize(request.name());
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vendor name is required");
        }
        vendor.setName(name);
        vendor.setCompany(normalize(request.company()));
        vendor.setEmail(normalize(request.email()));
        vendor.setPhone(normalize(request.phone()));
        vendor.setLogoUrl(normalize(request.logoUrl()));
        vendor.setStatus(VendorStatus.from(request.status()));

        CommissionType type = CommissionType.from(request.commissionType());
        BigDecimal rate = request.commissionRate() == null ? BigDecimal.ZERO : request.commissionRate();
        if (rate.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Commission rate cannot be negative");
        }
        if (type == CommissionType.PERCENTAGE && rate.compareTo(HUNDRED) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Commission percentage cannot exceed 100");
        }
        vendor.setCommissionType(type);
        vendor.setCommissionRate(rate);

        vendor.setPayoutAccountName(normalize(request.payoutAccountName()));
        vendor.setPayoutAccountNumber(normalize(request.payoutAccountNumber()));
        vendor.setPayoutIfsc(normalize(request.payoutIfsc()));
        vendor.setPayoutUpi(normalize(request.payoutUpi()));

        vendor.setAddressLine1(normalize(request.addressLine1()));
        vendor.setAddressLine2(normalize(request.addressLine2()));
        vendor.setCity(normalize(request.city()));
        vendor.setState(normalize(request.state()));
        vendor.setPincode(normalize(request.pincode()));
        vendor.setCountry(normalize(request.country()));
        vendor.setNotes(normalize(request.notes()));
    }

    private boolean matchesSearch(Vendor vendor, String query) {
        if (query.isEmpty()) {
            return true;
        }
        String haystack = String.join(
                        " ",
                        safe(vendor.getName()),
                        safe(vendor.getCompany()),
                        safe(vendor.getEmail()),
                        safe(vendor.getPhone()),
                        safe(vendor.getVendorCode()))
                .toLowerCase(Locale.ROOT);
        return haystack.contains(query);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private VendorData toData(Vendor v) {
        return new VendorData(
                v.getPublicVendorId(),
                v.getVendorCode(),
                v.getName(),
                v.getCompany(),
                v.getEmail(),
                v.getPhone(),
                v.getLogoUrl(),
                v.getStatus().apiValue(),
                v.getCommissionType().apiValue(),
                v.getCommissionRate(),
                v.getPayoutAccountName(),
                v.getPayoutAccountNumber(),
                v.getPayoutIfsc(),
                v.getPayoutUpi(),
                v.getAddressLine1(),
                v.getAddressLine2(),
                v.getCity(),
                v.getState(),
                v.getPincode(),
                v.getCountry(),
                v.getNotes(),
                v.getCreatedAt(),
                v.getUpdatedAt());
    }

    // --- Demo data seeding --------------------------------------------------

    /** Idempotently inserts demo vendors the first time a store has none. */
    private void ensureDemoData(String storeId, String ownerPublicUserId) {
        if (storeId == null || storeId.isBlank() || repository.countByStoreId(storeId) > 0) {
            return;
        }
        List<Vendor> seeds = new ArrayList<>();
        seeds.add(demo(storeId, ownerPublicUserId, "Urban Threads", "Urban Threads Pvt Ltd",
                "hello@urbanthreads.in", "+91 98200 11001", VendorStatus.APPROVED,
                CommissionType.PERCENTAGE, "12.00", "Mumbai", "Maharashtra"));
        seeds.add(demo(storeId, ownerPublicUserId, "GadgetHub", "GadgetHub Retail",
                "sales@gadgethub.in", "+91 99860 22002", VendorStatus.APPROVED,
                CommissionType.PERCENTAGE, "8.50", "Bengaluru", "Karnataka"));
        seeds.add(demo(storeId, ownerPublicUserId, "HomeNest Decor", "HomeNest",
                "care@homenest.in", "+91 90045 33003", VendorStatus.PENDING,
                CommissionType.PERCENTAGE, "15.00", "Jaipur", "Rajasthan"));
        seeds.add(demo(storeId, ownerPublicUserId, "FitZone Gear", "FitZone",
                "team@fitzone.in", "+91 98765 44004", VendorStatus.APPROVED,
                CommissionType.FIXED, "50.00", "Pune", "Maharashtra"));
        seeds.add(demo(storeId, ownerPublicUserId, "Bloom Beauty", "Bloom Beauty Co",
                "hi@bloombeauty.in", "+91 97400 55005", VendorStatus.SUSPENDED,
                CommissionType.PERCENTAGE, "18.00", "Delhi", "Delhi"));
        repository.saveAll(seeds);
    }

    private Vendor demo(
            String storeId,
            String ownerPublicUserId,
            String name,
            String company,
            String email,
            String phone,
            VendorStatus status,
            CommissionType commissionType,
            String rate,
            String city,
            String state) {
        Vendor v = new Vendor();
        v.setStoreId(storeId);
        v.setOwnerPublicUserId(ownerPublicUserId == null ? storeId : ownerPublicUserId);
        v.setName(name);
        v.setCompany(company);
        v.setEmail(email);
        v.setPhone(phone);
        v.setStatus(status);
        v.setCommissionType(commissionType);
        v.setCommissionRate(new BigDecimal(rate));
        v.setCity(city);
        v.setState(state);
        v.setCountry("India");
        return v;
    }
}
