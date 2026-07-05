package com.ecommerce.access;

import com.ecommerce.auth.AuthAudience;
import com.ecommerce.auth.CurrentAccountData;
import com.ecommerce.auth.CurrentAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single authorization chokepoint for domain controllers. Resolves the caller, verifies the
 * audience + store, and enforces a page permission — throwing {@code 403 FORBIDDEN} when the
 * caller's role lacks the required {@link AccessLevel}. The store owner always passes.
 */
@Service
public class AccessControlService {
    private final CurrentAccountService currentAccountService;

    public AccessControlService(CurrentAccountService currentAccountService) {
        this.currentAccountService = currentAccountService;
    }

    /**
     * Resolve and authorize a request.
     *
     * @param authorization the {@code Authorization} header
     * @param audience      the {@code {audience}} path segment (must match the session)
     * @param permissionKey the {@link PermissionCatalog} key the endpoint guards
     * @param required      the minimum access level the caller must hold
     * @return the tenant scope to operate within
     */
    public StoreAccessScope requireScope(
            String authorization, String audience, String permissionKey, AccessLevel required) {
        CurrentAccountData account = currentAccountService.resolveCurrentAccount(authorization);
        if (account.audience() != AuthAudience.from(audience)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audience mismatch");
        }
        if (account.store() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store setup required before continuing");
        }

        StoreAccessData access = account.access();
        boolean owner = access != null && access.isOwner();
        if (!owner) {
            // The store owner always passes; everyone else must hold the required level.
            AccessLevel granted = access == null
                    ? AccessLevel.NONE
                    : access.permissions().getOrDefault(permissionKey, AccessLevel.NONE);
            if (!granted.satisfies(required)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "You don't have access to this page");
            }
        }

        return new StoreAccessScope(
                account.store().storeId(),
                account.store().orgId(),
                account.user().publicUserId(),
                account.user().userId(),
                owner);
    }
}
