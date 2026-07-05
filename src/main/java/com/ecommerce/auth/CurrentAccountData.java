package com.ecommerce.auth;

import com.ecommerce.access.StoreAccessData;

public record CurrentAccountData(
        AuthAudience audience,
        CurrentAccountUserData user,
        BusinessStoreData store,
        boolean onboardingRequired,
        StoreAccessData access) {}
