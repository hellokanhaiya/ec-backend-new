package com.ecommerce.controller;

import com.ecommerce.auth.CurrentAccountData;
import com.ecommerce.auth.CurrentAccountService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final CurrentAccountService currentAccountService;

    public AccountController(CurrentAccountService currentAccountService) {
        this.currentAccountService = currentAccountService;
    }

    @GetMapping({"/store-info", "/auth/me"})
    public ResponseEntity<Map<String, Object>> currentAccount(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        CurrentAccountData data = currentAccountService.resolveCurrentAccount(authorization);
        return ok("Store info loaded", data);
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
