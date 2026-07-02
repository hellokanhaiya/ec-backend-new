package com.ecommerce.controller;

import com.ecommerce.auth.AuthAudience;
import com.ecommerce.auth.CurrentAccountData;
import com.ecommerce.auth.CurrentAccountService;
import com.ecommerce.tax.TaxRatesData;
import com.ecommerce.tax.TaxService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Serves the tax classes available when creating/editing a product, keyed off the
 * store's country (India → GST slabs + HSN). Read-only reference data. */
@RestController
@RequestMapping("/api/v1")
public class TaxController {
    private final TaxService taxService;
    private final CurrentAccountService currentAccountService;

    public TaxController(TaxService taxService, CurrentAccountService currentAccountService) {
        this.taxService = taxService;
        this.currentAccountService = currentAccountService;
    }

    @GetMapping("/{audience}/auth/tax/rates")
    public ResponseEntity<Map<String, Object>> rates(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        CurrentAccountData currentAccount = currentAccountService.resolveCurrentAccount(authorization);
        if (currentAccount.audience() != AuthAudience.from(audience)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audience mismatch");
        }
        String countryCode = currentAccount.store() == null ? null : currentAccount.store().countryCode();
        TaxRatesData data = taxService.rates(countryCode);
        return ok("Tax rates loaded", data);
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
