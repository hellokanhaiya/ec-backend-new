package com.ecommerce.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecommerce.auth.AuthAudience;
import com.ecommerce.auth.BusinessStoreData;
import com.ecommerce.auth.CurrentAccountData;
import com.ecommerce.auth.CurrentAccountService;
import com.ecommerce.auth.CurrentAccountUserData;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** The 403-vs-pass decision every guarded controller relies on. */
class AccessControlServiceTest {
    private CurrentAccountService currentAccountService;
    private AccessControlService accessControl;

    @BeforeEach
    void setUp() {
        currentAccountService = mock(CurrentAccountService.class);
        accessControl = new AccessControlService(currentAccountService);
    }

    @Test
    void ownerPassesEveryCheck() {
        stub(true, Map.of());

        StoreAccessScope scope = accessControl.requireScope(
                "Bearer t", "admin", PermissionCatalog.SETTINGS_USERS, AccessLevel.MANAGE);

        assertThat(scope.owner()).isTrue();
        assertThat(scope.storeId()).isEqualTo("store-1");
    }

    @Test
    void memberWithSufficientLevelPasses() {
        stub(false, Map.of(PermissionCatalog.ORDERS_ALL, AccessLevel.EDIT));

        StoreAccessScope scope = accessControl.requireScope(
                "Bearer t", "admin", PermissionCatalog.ORDERS_ALL, AccessLevel.VIEW);

        assertThat(scope.owner()).isFalse();
    }

    @Test
    void memberWithoutRequiredLevelIsForbidden() {
        stub(false, Map.of(PermissionCatalog.ORDERS_ALL, AccessLevel.VIEW));

        assertThatThrownBy(() -> accessControl.requireScope(
                "Bearer t", "admin", PermissionCatalog.ORDERS_ALL, AccessLevel.MANAGE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void missingPermissionKeyIsForbidden() {
        stub(false, Map.of(PermissionCatalog.ORDERS_ALL, AccessLevel.MANAGE));

        assertThatThrownBy(() -> accessControl.requireScope(
                "Bearer t", "admin", PermissionCatalog.SETTINGS_USERS, AccessLevel.VIEW))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    private void stub(boolean owner, Map<String, AccessLevel> permissions) {
        BusinessStoreData store = new BusinessStoreData(
                "user-1", "ORG-1", "store-1", "Biz", null, null, null, "GROCERY", "Grocery", null,
                "INR", "IN", "India", null, null, null, null, null, null, null, null, null, null, null, "ADMIN");
        CurrentAccountUserData user = new CurrentAccountUserData(
                1L, "user-1", "Owner", "owner@store.com", null, "ACTIVE", null, null);
        StoreAccessData access = new StoreAccessData(
                owner ? PermissionCatalog.ROLE_OWNER : "support",
                owner ? "Owner" : "Support",
                owner,
                MemberStatus.ACTIVE,
                permissions);
        CurrentAccountData data = new CurrentAccountData(AuthAudience.ADMIN, user, store, false, access);
        when(currentAccountService.resolveCurrentAccount("Bearer t")).thenReturn(data);
    }
}
