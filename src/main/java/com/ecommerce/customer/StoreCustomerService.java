package com.ecommerce.customer;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.ecommerce.tag.StoreTagService;
import com.ecommerce.tag.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StoreCustomerService {
    private final CustomerRepository customerRepository;
    private final StoreTagService storeTagService;

    public StoreCustomerService(CustomerRepository customerRepository, StoreTagService storeTagService) {
        this.customerRepository = customerRepository;
        this.storeTagService = storeTagService;
    }

    public CustomerListData list(
            String storeId, String search, String status, String dateFrom, String dateTo, int page, int size) {
        List<Customer> all = customerRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        CustomerStatus statusFilter =
                (status == null || status.isBlank() || status.equalsIgnoreCase("all")) ? null : CustomerStatus.from(status);
        Instant createdFrom = parseInstant(dateFrom);
        Instant createdTo = parseInstant(dateTo);

        List<Customer> filtered = all.stream()
                .filter(customer -> statusFilter == null || customer.getStatus() == statusFilter)
                .filter(customer -> query.isEmpty() || searchable(customer).contains(query))
                .filter(customer -> withinRange(customer.getCreatedAt(), createdFrom, createdTo))
                .toList();

        long total = filtered.size();
        List<Customer> pageItems;
        if (size <= 0) {
            pageItems = filtered;
        } else {
            int from = Math.max(0, (Math.max(page, 1) - 1) * size);
            int to = Math.min(filtered.size(), from + size);
            pageItems = from >= filtered.size() ? List.of() : filtered.subList(from, to);
        }

        List<CustomerSummaryData> items = pageItems.stream().map(this::toSummary).toList();
        return new CustomerListData(items, total, Math.max(page, 1), size);
    }

    public CustomerOverviewData overview(String storeId) {
        List<Customer> all = customerRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        long total = all.size();
        long active = all.stream().filter(c -> c.getStatus() == CustomerStatus.ACTIVE).count();
        long inactive = all.stream().filter(c -> c.getStatus() == CustomerStatus.INACTIVE).count();
        long blocked = all.stream().filter(c -> c.getStatus() == CustomerStatus.BLOCKED).count();
        var monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long newThisMonth = all.stream()
                .filter(c -> c.getCreatedAt() != null && !c.getCreatedAt().isBefore(monthStart))
                .count();
        return new CustomerOverviewData(total, active, inactive, blocked, newThisMonth);
    }

    public CustomerData get(String storeId, String publicCustomerId) {
        return toData(require(storeId, publicCustomerId));
    }

    public CustomerData create(String storeId, String ownerPublicUserId, CustomerRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        Customer customer = new Customer();
        customer.setStoreId(storeId);
        customer.setOwnerPublicUserId(ownerPublicUserId);
        applyRequest(customer, request, storeId);

        Customer saved = customerRepository.save(customer);
        if (saved.getCustomerCode() == null || saved.getCustomerCode().isBlank()) {
            saved.setCustomerCode(String.format("CUS-%05d", saved.getId()));
            saved = customerRepository.save(saved);
        }
        return toData(saved);
    }

    public CustomerData update(String storeId, String publicCustomerId, CustomerRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        Customer customer = require(storeId, publicCustomerId);
        applyRequest(customer, request, storeId);
        return toData(customerRepository.save(customer));
    }

    public void delete(String storeId, String publicCustomerId) {
        customerRepository.delete(require(storeId, publicCustomerId));
    }

    /** Delete several customers at once (only those belonging to this store). */
    public int bulkDelete(String storeId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Set<String> idSet = new HashSet<>(ids);
        List<Customer> toDelete = customerRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .filter(c -> idSet.contains(c.getPublicCustomerId()))
                .toList();
        customerRepository.deleteAll(toDelete);
        return toDelete.size();
    }

    /** Flattened export rows for the whole store, or just the given ids. */
    public List<CustomerExportRow> exportRows(String storeId, List<String> ids) {
        List<Customer> all = customerRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        List<Customer> selected;
        if (ids == null || ids.isEmpty()) {
            selected = all;
        } else {
            Set<String> idSet = new HashSet<>(ids);
            selected = all.stream().filter(c -> idSet.contains(c.getPublicCustomerId())).toList();
        }
        return selected.stream().map(this::toExportRow).toList();
    }

    /**
     * Import (create/update) customers from parsed CSV rows. Each row is validated
     * independently; invalid rows are collected as errors and do not abort the batch.
     * Rows matching an existing customer (email or phone) are updated when
     * {@code overwrite} is true, otherwise skipped.
     */
    public ImportResultData importCustomers(
            String storeId, String ownerPublicUserId, boolean overwrite, List<CustomerImportRow> rows) {
        List<CustomerImportRow> safeRows = rows == null ? List.of() : rows;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        List<ImportResultData.ImportError> errors = new ArrayList<>();

        // Lookup maps from existing store customers so we can match by email / phone.
        Map<String, Customer> byEmail = new HashMap<>();
        Map<String, Customer> byPhone = new HashMap<>();
        for (Customer c : customerRepository.findByStoreIdOrderByCreatedAtDesc(storeId)) {
            if (c.getEmail() != null && !c.getEmail().isBlank()) {
                byEmail.putIfAbsent(c.getEmail().toLowerCase(Locale.ROOT), c);
            }
            if (c.getPhone() != null && !c.getPhone().isBlank()) {
                byPhone.putIfAbsent(c.getPhone().trim(), c);
            }
        }

        for (CustomerImportRow row : safeRows) {
            int rowNumber = row.rowNumber() != null ? row.rowNumber() : 0;
            String identifier = firstNonBlank(row.email(), row.phone(), row.firstName(), "Row " + rowNumber);
            try {
                String firstName = row.firstName() == null ? null : row.firstName().trim();
                if (firstName == null || firstName.isEmpty()) {
                    errors.add(new ImportResultData.ImportError(rowNumber, identifier, "First Name is required"));
                    failed++;
                    continue;
                }
                String email = normalize(row.email());
                if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
                    errors.add(new ImportResultData.ImportError(rowNumber, identifier, "Email is not valid: " + email));
                    failed++;
                    continue;
                }

                BigDecimal wallet;
                BigDecimal storeCredit;
                Integer rewardPoints;
                try {
                    wallet = parseDecimal(row.wallet(), "Wallet");
                    storeCredit = parseDecimal(row.storeCredit(), "Store Credit");
                    rewardPoints = parseInteger(row.rewardPoints(), "Reward Points");
                } catch (NumberFormatException nfe) {
                    errors.add(new ImportResultData.ImportError(rowNumber, identifier, nfe.getMessage()));
                    failed++;
                    continue;
                }

                CustomerRequest request = new CustomerRequest(
                        firstName,
                        normalize(row.lastName()),
                        email,
                        normalize(row.phoneCountryCode()),
                        normalize(row.phone()),
                        parseBool(row.acceptsEmail()),
                        parseBool(row.acceptsSms()),
                        parseBool(row.acceptsWhatsapp()),
                        parseBool(row.acceptsPromos()),
                        normalize(row.status()),
                        wallet,
                        rewardPoints,
                        storeCredit,
                        normalize(row.note()),
                        splitTags(row.tags()),
                        buildImportAddress(row));

                Customer match = null;
                if (email != null) {
                    match = byEmail.get(email.toLowerCase(Locale.ROOT));
                }
                if (match == null && request.phone() != null) {
                    match = byPhone.get(request.phone());
                }

                if (match != null) {
                    if (overwrite) {
                        applyRequest(match, request, storeId);
                        customerRepository.save(match);
                        updated++;
                    } else {
                        skipped++;
                    }
                } else {
                    Customer customer = new Customer();
                    customer.setStoreId(storeId);
                    customer.setOwnerPublicUserId(ownerPublicUserId);
                    applyRequest(customer, request, storeId);
                    Customer saved = customerRepository.save(customer);
                    if (saved.getCustomerCode() == null || saved.getCustomerCode().isBlank()) {
                        saved.setCustomerCode(String.format("CUS-%05d", saved.getId()));
                        saved = customerRepository.save(saved);
                    }
                    // Register so later rows in the same file dedupe against it.
                    if (email != null) {
                        byEmail.putIfAbsent(email.toLowerCase(Locale.ROOT), saved);
                    }
                    if (request.phone() != null) {
                        byPhone.putIfAbsent(request.phone(), saved);
                    }
                    created++;
                }
            } catch (Exception ex) {
                errors.add(new ImportResultData.ImportError(
                        rowNumber, identifier, ex.getMessage() == null ? "Import failed" : ex.getMessage()));
                failed++;
            }
        }

        return new ImportResultData(safeRows.size(), created, updated, skipped, failed, errors);
    }

    // --- helpers -----------------------------------------------------------

    private Customer require(String storeId, String publicCustomerId) {
        return customerRepository.findByStoreIdAndPublicCustomerId(storeId, publicCustomerId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Customer not found"));
    }

    private void applyRequest(Customer customer, CustomerRequest request, String storeId) {
        String firstName = request.firstName() == null ? null : request.firstName().trim();
        if (firstName == null || firstName.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "First name is required");
        }
        customer.setFirstName(firstName);
        customer.setLastName(normalize(request.lastName()));
        customer.setEmail(normalize(request.email()));
        customer.setPhoneCountryCode(normalize(request.phoneCountryCode()));
        customer.setPhone(normalize(request.phone()));
        customer.setAcceptsEmail(Boolean.TRUE.equals(request.acceptsEmail()));
        customer.setAcceptsSms(Boolean.TRUE.equals(request.acceptsSms()));
        customer.setAcceptsWhatsapp(Boolean.TRUE.equals(request.acceptsWhatsapp()));
        customer.setAcceptsPromos(Boolean.TRUE.equals(request.acceptsPromos()));
        customer.setStatus(CustomerStatus.from(request.status()));
        customer.setWallet(request.wallet() != null ? request.wallet() : BigDecimal.ZERO);
        customer.setRewardPoints(request.rewardPoints() != null ? request.rewardPoints() : 0);
        customer.setStoreCredit(request.storeCredit() != null ? request.storeCredit() : BigDecimal.ZERO);
        customer.setNotes(normalize(request.notes()));

        // Tags: upsert into the store's shared tag library and link to this customer.
        customer.getTags().clear();
        if (request.tags() != null) {
            for (String name : request.tags()) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                customer.getTags().add(storeTagService.findOrCreate(storeId, name));
            }
        }

        applyAddresses(customer, request.addresses());
    }

    private void applyAddresses(Customer customer, List<CustomerAddressRequest> requests) {
        customer.getAddresses().clear();
        if (requests == null || requests.isEmpty()) {
            return;
        }
        List<CustomerAddress> built = new ArrayList<>();
        boolean anyDefault = false;
        for (CustomerAddressRequest request : requests) {
            if (request == null || isEmptyAddress(request)) {
                continue;
            }
            CustomerAddress address = new CustomerAddress();
            address.setType(AddressType.from(request.type()));
            boolean isDefault = Boolean.TRUE.equals(request.isDefault());
            address.setDefaultAddress(isDefault);
            anyDefault = anyDefault || isDefault;
            address.setCountry(normalize(request.country()));
            address.setFirstName(normalize(request.firstName()));
            address.setLastName(normalize(request.lastName()));
            address.setCompany(normalize(request.company()));
            address.setAddressLine1(normalize(request.addressLine1()));
            address.setAddressLine2(normalize(request.addressLine2()));
            address.setCity(normalize(request.city()));
            address.setState(normalize(request.state()));
            address.setPostalCode(normalize(request.postalCode()));
            address.setPhoneCountryCode(normalize(request.phoneCountryCode()));
            address.setPhone(normalize(request.phone()));
            built.add(address);
        }
        if (built.isEmpty()) {
            return;
        }
        // Guarantee exactly one default address.
        if (!anyDefault) {
            built.get(0).setDefaultAddress(true);
        } else {
            boolean seen = false;
            for (CustomerAddress address : built) {
                if (address.isDefaultAddress()) {
                    if (seen) {
                        address.setDefaultAddress(false);
                    } else {
                        seen = true;
                    }
                }
            }
        }
        customer.getAddresses().addAll(built);
    }

    private boolean isEmptyAddress(CustomerAddressRequest request) {
        return isBlank(request.addressLine1())
                && isBlank(request.city())
                && isBlank(request.state())
                && isBlank(request.postalCode())
                && isBlank(request.country());
    }

    private String searchable(Customer customer) {
        return String.join(
                        " ",
                        nullToEmpty(customer.getFirstName()),
                        nullToEmpty(customer.getLastName()),
                        nullToEmpty(customer.getEmail()),
                        nullToEmpty(customer.getPhone()),
                        nullToEmpty(customer.getCustomerCode()))
                .toLowerCase(Locale.ROOT);
    }

    private CustomerData toData(Customer customer) {
        return new CustomerData(
                customer.getPublicCustomerId(),
                customer.getCustomerCode(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhoneCountryCode(),
                customer.getPhone(),
                customer.isAcceptsEmail(),
                customer.isAcceptsSms(),
                customer.isAcceptsWhatsapp(),
                customer.isAcceptsPromos(),
                customer.getStatus().name(),
                customer.getWallet(),
                customer.getRewardPoints(),
                customer.getStoreCredit(),
                customer.getNotes(),
                customer.getTags().stream().map(Tag::getName).toList(),
                customer.getAddresses().stream().map(this::toAddressData).toList(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }

    private CustomerAddressData toAddressData(CustomerAddress address) {
        return new CustomerAddressData(
                address.getId(),
                address.getType().name(),
                address.isDefaultAddress(),
                address.getCountry(),
                address.getFirstName(),
                address.getLastName(),
                address.getCompany(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getPhoneCountryCode(),
                address.getPhone());
    }

    private CustomerSummaryData toSummary(Customer customer) {
        String location = customer.getAddresses().stream()
                .filter(CustomerAddress::isDefaultAddress)
                .findFirst()
                .or(() -> customer.getAddresses().stream().findFirst())
                .map(this::formatLocation)
                .orElse(null);
        return new CustomerSummaryData(
                customer.getPublicCustomerId(),
                customer.getCustomerCode(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhoneCountryCode(),
                customer.getPhone(),
                location,
                customer.getStatus().name(),
                customer.getTags().stream().map(Tag::getName).toList(),
                0,
                BigDecimal.ZERO,
                customer.getWallet(),
                customer.getCreatedAt());
    }

    private String formatLocation(CustomerAddress address) {
        String city = address.getCity();
        String state = address.getState();
        if (city != null && !city.isBlank() && state != null && !state.isBlank()) {
            return city + ", " + state;
        }
        if (city != null && !city.isBlank()) {
            return city;
        }
        return (state == null || state.isBlank()) ? null : state;
    }

    /** Parse an ISO-8601 instant (e.g. "2026-07-01T00:00:00.000Z") sent by the
     * date-range filter. Blank means "no bound"; a malformed value is a client error. */
    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid date filter value: " + value);
        }
    }

    /** Inclusive [from, to] window on a customer's creation time; null bounds are open-ended. */
    private static boolean withinRange(Instant createdAt, Instant from, Instant to) {
        if (from == null && to == null) {
            return true;
        }
        if (createdAt == null) {
            return false;
        }
        if (from != null && createdAt.isBefore(from)) {
            return false;
        }
        return to == null || !createdAt.isAfter(to);
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // --- import / export helpers -------------------------------------------

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private CustomerExportRow toExportRow(Customer c) {
        CustomerAddress def = c.getAddresses().stream()
                .filter(CustomerAddress::isDefaultAddress)
                .findFirst()
                .or(() -> c.getAddresses().stream().findFirst())
                .orElse(null);
        String tags = c.getTags().stream().map(Tag::getName).collect(Collectors.joining(","));
        return new CustomerExportRow(
                c.getCustomerCode(),
                c.getFirstName(),
                c.getLastName(),
                c.getEmail(),
                c.getPhoneCountryCode(),
                c.getPhone(),
                c.isAcceptsEmail(),
                c.isAcceptsSms(),
                c.isAcceptsWhatsapp(),
                c.isAcceptsPromos(),
                c.getStatus().name(),
                c.getWallet(),
                c.getRewardPoints(),
                c.getStoreCredit(),
                tags,
                c.getNotes(),
                def != null ? def.getCompany() : null,
                def != null ? def.getAddressLine1() : null,
                def != null ? def.getAddressLine2() : null,
                def != null ? def.getCity() : null,
                def != null ? def.getState() : null,
                def != null ? def.getCountry() : null,
                def != null ? def.getPostalCode() : null,
                def != null ? def.getPhone() : null,
                BigDecimal.ZERO,
                0);
    }

    private static List<CustomerAddressRequest> buildImportAddress(CustomerImportRow row) {
        boolean hasAddress = !isBlank(row.addressLine1())
                || !isBlank(row.city())
                || !isBlank(row.province())
                || !isBlank(row.zip())
                || !isBlank(row.countryCode())
                || !isBlank(row.company());
        if (!hasAddress) {
            return List.of();
        }
        return List.of(new CustomerAddressRequest(
                "SHIPPING",
                Boolean.TRUE,
                normalize(row.countryCode()),
                null,
                null,
                normalize(row.company()),
                normalize(row.addressLine1()),
                normalize(row.addressLine2()),
                normalize(row.city()),
                normalize(row.province()),
                normalize(row.zip()),
                normalize(row.phoneCountryCode()),
                normalize(row.addressPhone())));
    }

    private static Boolean parseBool(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("yes") || v.equals("true") || v.equals("1") || v.equals("y");
    }

    private static BigDecimal parseDecimal(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            throw new NumberFormatException(field + " must be a number: " + value);
        }
    }

    private static Integer parseInteger(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new NumberFormatException(field + " must be a whole number: " + value);
        }
    }

    private static List<String> splitTags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }
}
