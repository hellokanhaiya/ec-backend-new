package com.ecommerce.auth;

public record CurrentAccountData(
        AuthAudience audience,
        CurrentAccountUserData user,
        BusinessStoreData store,
        boolean onboardingRequired) {}
