package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.MemberData;
import com.ecommerce.access.MemberInviteRequest;
import com.ecommerce.access.MemberUpdateRequest;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.PermissionRule;
import com.ecommerce.access.RoleData;
import com.ecommerce.access.RoleRequest;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.access.StoreAccessService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Users &amp; Roles management. Reads require {@code settings.users} VIEW; writes require MANAGE
 * (the store owner always passes). Backs the frontend Users &amp; Roles settings screen.
 */
@RestController
@RequestMapping("/api/v1")
public class AccessController {
    private final StoreAccessService accessService;
    private final AccessControlService accessControl;

    public AccessController(StoreAccessService accessService, AccessControlService accessControl) {
        this.accessService = accessService;
        this.accessControl = accessControl;
    }

    // --- Catalog ------------------------------------------------------------

    @GetMapping("/{audience}/auth/access/catalog")
    public ResponseEntity<Map<String, Object>> catalog(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        accessControl.requireScope(authorization, audience, PermissionCatalog.SETTINGS_USERS, AccessLevel.VIEW);
        List<PermissionRule> rules = accessService.getCatalog();
        return ok("Permission catalog loaded", rules);
    }

    // --- Roles --------------------------------------------------------------

    @GetMapping("/{audience}/auth/access/roles")
    public ResponseEntity<Map<String, Object>> listRoles(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = read(authorization, audience);
        List<RoleData> data = accessService.listRoles(scope.storeId());
        return ok("Roles loaded", data);
    }

    @PostMapping("/{audience}/auth/access/roles")
    public ResponseEntity<Map<String, Object>> createRole(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody RoleRequest request) {
        StoreAccessScope scope = manage(authorization, audience);
        RoleData data = accessService.createRole(scope.storeId(), request);
        return ok("Role created", data);
    }

    @PutMapping("/{audience}/auth/access/roles/{publicId}")
    public ResponseEntity<Map<String, Object>> updateRole(
            @PathVariable String audience,
            @PathVariable String publicId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody RoleRequest request) {
        StoreAccessScope scope = manage(authorization, audience);
        RoleData data = accessService.updateRole(scope.storeId(), publicId, request);
        return ok("Role updated", data);
    }

    @DeleteMapping("/{audience}/auth/access/roles/{publicId}")
    public ResponseEntity<Map<String, Object>> deleteRole(
            @PathVariable String audience,
            @PathVariable String publicId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = manage(authorization, audience);
        accessService.deleteRole(scope.storeId(), publicId);
        return ok("Role deleted", null);
    }

    @PostMapping("/{audience}/auth/access/roles/{publicId}/clone")
    public ResponseEntity<Map<String, Object>> cloneRole(
            @PathVariable String audience,
            @PathVariable String publicId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, String> body) {
        StoreAccessScope scope = manage(authorization, audience);
        String newName = body.getOrDefault("name", "Cloned Role");
        RoleData data = accessService.cloneRole(scope.storeId(), publicId, newName);
        return ok("Role cloned", data);
    }

    // --- Members ------------------------------------------------------------

    @GetMapping("/{audience}/auth/access/members")
    public ResponseEntity<Map<String, Object>> listMembers(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = read(authorization, audience);
        List<MemberData> data = accessService.listMembers(scope.storeId());
        return ok("Members loaded", data);
    }

    @PostMapping("/{audience}/auth/access/members")
    public ResponseEntity<Map<String, Object>> inviteMember(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody MemberInviteRequest request) {
        StoreAccessScope scope = manage(authorization, audience);
        MemberData data = accessService.inviteMember(scope.storeId(), request);
        return ok("Member invited", data);
    }

    @PutMapping("/{audience}/auth/access/members/{publicId}")
    public ResponseEntity<Map<String, Object>> updateMember(
            @PathVariable String audience,
            @PathVariable String publicId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody MemberUpdateRequest request) {
        StoreAccessScope scope = manage(authorization, audience);
        MemberData data = accessService.updateMember(scope.storeId(), publicId, request);
        return ok("Member updated", data);
    }

    @DeleteMapping("/{audience}/auth/access/members/{publicId}")
    public ResponseEntity<Map<String, Object>> removeMember(
            @PathVariable String audience,
            @PathVariable String publicId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = manage(authorization, audience);
        accessService.removeMember(scope.storeId(), publicId);
        return ok("Member removed", null);
    }

    // --- Helpers ------------------------------------------------------------

    private StoreAccessScope read(String authorization, String audience) {
        return accessControl.requireScope(authorization, audience, PermissionCatalog.SETTINGS_USERS, AccessLevel.VIEW);
    }

    private StoreAccessScope manage(String authorization, String audience) {
        return accessControl.requireScope(authorization, audience, PermissionCatalog.SETTINGS_USERS, AccessLevel.MANAGE);
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
