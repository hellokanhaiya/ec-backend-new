package com.ecommerce.access;

import com.ecommerce.auth.repository.AdminAuthUserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Roles, members, and effective-permission resolution for a store.
 *
 * <p>Access is page-based: each role carries an {@link AccessLevel} per permission key
 * ({@link PermissionCatalog}). The store owner always resolves to full {@code MANAGE} regardless of
 * role rows, so an owner can never lock themselves out. Members are seeded with five default roles
 * on first touch; invites link to an admin user on first OTP sign-in.
 */
@Service
@Transactional
public class StoreAccessService {
    private final StoreRoleRepository roleRepository;
    private final StoreRolePermissionRepository permissionRepository;
    private final StoreMemberRepository memberRepository;
    private final AdminAuthUserRepository adminAuthUserRepository;

    public StoreAccessService(
            StoreRoleRepository roleRepository,
            StoreRolePermissionRepository permissionRepository,
            StoreMemberRepository memberRepository,
            AdminAuthUserRepository adminAuthUserRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.memberRepository = memberRepository;
        this.adminAuthUserRepository = adminAuthUserRepository;
    }

    // --- Seeding ------------------------------------------------------------

    /** Idempotently seed the five default roles and the owner member for a store. */
    public void ensureDefaultsForStore(String storeId, Long ownerUserId, String ownerEmail, String ownerName) {
        if (!roleRepository.existsByStoreId(storeId)) {
            for (RoleTemplate template : PermissionCatalog.defaultRoles()) {
                StoreRole role = new StoreRole();
                role.setStoreId(storeId);
                role.setRoleKey(template.roleKey());
                role.setName(template.name());
                role.setDescription(template.description());
                role.setSystemRole(template.systemRole());
                role.setLocked(template.locked());
                StoreRole saved = roleRepository.save(role);
                savePermissions(saved.getId(), template.pages());
            }
        }
        ensureOwnerMember(storeId, ownerUserId, ownerEmail, ownerName);
    }

    private void ensureOwnerMember(String storeId, Long ownerUserId, String ownerEmail, String ownerName) {
        if (ownerUserId == null) {
            return;
        }
        if (memberRepository.findByStoreIdAndAdminUserId(storeId, ownerUserId).isPresent()) {
            return;
        }
        StoreRole ownerRole = roleRepository.findByStoreIdAndRoleKey(storeId, PermissionCatalog.ROLE_OWNER)
                .orElseThrow(() -> new IllegalStateException("Owner role missing for store " + storeId));
        StoreMember member = new StoreMember();
        member.setStoreId(storeId);
        member.setAdminUserId(ownerUserId);
        member.setEmail(ownerEmail);
        member.setDisplayName(ownerName);
        member.setTitle("Owner");
        member.setRoleId(ownerRole.getId());
        member.setStatus(MemberStatus.ACTIVE);
        member.setLastActiveAt(Instant.now());
        memberRepository.save(member);
    }

    // --- Resolution ---------------------------------------------------------

    /**
     * Effective access for a user in a store. Store owners always get full {@code MANAGE}. A
     * non-member, or a PAUSED member, gets an empty matrix (no access).
     */
    @Transactional(readOnly = true)
    public StoreAccessData resolveAccess(String storeId, Long adminUserId, boolean isOwner) {
        if (isOwner) {
            String ownerName = roleRepository.findByStoreIdAndRoleKey(storeId, PermissionCatalog.ROLE_OWNER)
                    .map(StoreRole::getName)
                    .orElse("Owner");
            return new StoreAccessData(PermissionCatalog.ROLE_OWNER, ownerName, true, MemberStatus.ACTIVE,
                    PermissionCatalog.uniformMatrix(AccessLevel.MANAGE));
        }

        StoreMember member = memberRepository.findByStoreIdAndAdminUserId(storeId, adminUserId).orElse(null);
        if (member == null) {
            return new StoreAccessData(null, null, false, MemberStatus.PAUSED, Map.of());
        }
        StoreRole role = roleRepository.findById(member.getRoleId()).orElse(null);
        String roleKey = role != null ? role.getRoleKey() : null;
        String roleName = role != null ? role.getName() : null;
        if (member.getStatus() != MemberStatus.ACTIVE || role == null) {
            return new StoreAccessData(roleKey, roleName, false, member.getStatus(), Map.of());
        }
        return new StoreAccessData(roleKey, roleName, false, MemberStatus.ACTIVE, permissionsForRole(role.getId()));
    }

    /** The storeId of the user's first active membership (for non-owner staff), if any. */
    @Transactional(readOnly = true)
    public Optional<String> findMemberStoreId(Long adminUserId) {
        if (adminUserId == null) {
            return Optional.empty();
        }
        return memberRepository.findByAdminUserId(adminUserId).stream()
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .map(StoreMember::getStoreId)
                .findFirst();
    }

    /** Link any pending email invites to a freshly signed-in admin user and activate them. */
    public void linkPendingInvites(Long adminUserId, String email) {
        if (adminUserId == null || email == null || email.isBlank()) {
            return;
        }
        List<StoreMember> pending = memberRepository.findByEmailIgnoreCaseAndStatus(email.trim(), MemberStatus.INVITED);
        for (StoreMember member : pending) {
            member.setAdminUserId(adminUserId);
            member.setStatus(MemberStatus.ACTIVE);
            member.setLastActiveAt(Instant.now());
            memberRepository.save(member);
        }
    }

    // --- Catalog ------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PermissionRule> getCatalog() {
        return PermissionCatalog.rules();
    }

    // --- Roles CRUD ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RoleData> listRoles(String storeId) {
        List<StoreRole> roles = roleRepository.findByStoreIdOrderByCreatedAtAsc(storeId);
        List<RoleData> out = new ArrayList<>(roles.size());
        for (StoreRole role : roles) {
            out.add(toRoleData(role));
        }
        return out;
    }

    public RoleData createRole(String storeId, RoleRequest request) {
        String name = requireText(request == null ? null : request.name(), "Role name is required");
        StoreRole role = new StoreRole();
        role.setStoreId(storeId);
        role.setRoleKey(uniqueRoleKey(storeId, name));
        role.setName(name);
        role.setDescription(request.description());
        role.setSystemRole(false);
        role.setLocked(false);
        StoreRole saved = roleRepository.save(role);
        Map<String, AccessLevel> matrix = PermissionCatalog.uniformMatrix(AccessLevel.NONE);
        overlay(matrix, request.pages());
        savePermissions(saved.getId(), matrix);
        return toRoleData(saved);
    }

    public RoleData updateRole(String storeId, String publicId, RoleRequest request) {
        StoreRole role = roleRepository.findByStoreIdAndPublicId(storeId, publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        if (role.isLocked()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The Owner role cannot be edited");
        }
        if (request != null && request.name() != null && !request.name().isBlank()) {
            role.setName(request.name().trim());
        }
        if (request != null && request.description() != null) {
            role.setDescription(request.description());
        }
        roleRepository.save(role);
        if (request != null && request.pages() != null && !request.pages().isEmpty()) {
            Map<String, AccessLevel> matrix = permissionsForRole(role.getId());
            overlay(matrix, request.pages());
            permissionRepository.deleteByRoleId(role.getId());
            savePermissions(role.getId(), matrix);
        }
        return toRoleData(role);
    }

    public void deleteRole(String storeId, String publicId) {
        StoreRole role = roleRepository.findByStoreIdAndPublicId(storeId, publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        if (role.isLocked() || role.isSystemRole()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Built-in roles cannot be deleted");
        }
        if (!memberRepository.findByRoleId(role.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reassign members before deleting this role");
        }
        permissionRepository.deleteByRoleId(role.getId());
        roleRepository.delete(role);
    }

    public RoleData cloneRole(String storeId, String publicId, String newName) {
        StoreRole source = roleRepository.findByStoreIdAndPublicId(storeId, publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        String name = requireText(newName, "Role name is required");
        StoreRole cloned = new StoreRole();
        cloned.setStoreId(storeId);
        cloned.setRoleKey(uniqueRoleKey(storeId, name));
        cloned.setName(name);
        cloned.setDescription(source.getDescription() != null ? source.getDescription() + " (copy)" : "Copy of " + source.getName());
        cloned.setSystemRole(false);
        cloned.setLocked(false);
        StoreRole saved = roleRepository.save(cloned);
        Map<String, AccessLevel> sourceMatrix = permissionsForRole(source.getId());
        savePermissions(saved.getId(), sourceMatrix);
        return toRoleData(saved);
    }

    // --- Members CRUD -------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MemberData> listMembers(String storeId) {
        List<StoreMember> members = memberRepository.findByStoreIdOrderByCreatedAtAsc(storeId);
        Map<Long, StoreRole> roleById = new LinkedHashMap<>();
        for (StoreRole role : roleRepository.findByStoreIdOrderByCreatedAtAsc(storeId)) {
            roleById.put(role.getId(), role);
        }
        List<MemberData> out = new ArrayList<>(members.size());
        for (StoreMember member : members) {
            out.add(toMemberData(member, roleById.get(member.getRoleId())));
        }
        return out;
    }

    public MemberData inviteMember(String storeId, MemberInviteRequest request) {
        String email = requireText(request == null ? null : request.email(), "Email is required").toLowerCase(Locale.ROOT);
        StoreRole role = resolveRole(storeId, request.rolePublicId());
        if (memberRepository.findByStoreIdAndEmailIgnoreCase(storeId, email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That email is already a member of this store");
        }
        StoreMember member = new StoreMember();
        member.setStoreId(storeId);
        member.setEmail(email);
        member.setDisplayName(request.displayName());
        member.setTitle(request.title());
        member.setRoleId(role.getId());
        member.setInvitedAt(Instant.now());
        // If the person already has an admin account, link and activate immediately.
        Optional<Long> existingUser = adminAuthUserRepository.findByEmailIgnoreCase(email)
                .map(user -> user.getId());
        if (existingUser.isPresent()) {
            member.setAdminUserId(existingUser.get());
            member.setStatus(MemberStatus.ACTIVE);
            member.setLastActiveAt(Instant.now());
        } else {
            member.setStatus(MemberStatus.INVITED);
        }
        StoreMember saved = memberRepository.save(member);
        return toMemberData(saved, role);
    }

    public MemberData updateMember(String storeId, String publicId, MemberUpdateRequest request) {
        StoreMember member = memberRepository.findByStoreIdAndPublicId(storeId, publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        StoreRole currentRole = roleRepository.findById(member.getRoleId()).orElse(null);
        boolean isOwnerMember = currentRole != null && currentRole.isLocked();
        if (isOwnerMember) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The store owner cannot be modified");
        }
        if (request != null && request.rolePublicId() != null && !request.rolePublicId().isBlank()) {
            StoreRole role = resolveRole(storeId, request.rolePublicId());
            if (role.isLocked()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign the Owner role");
            }
            member.setRoleId(role.getId());
        }
        if (request != null && request.status() != null) {
            member.setStatus(request.status());
        }
        if (request != null && request.title() != null) {
            member.setTitle(request.title());
        }
        StoreMember saved = memberRepository.save(member);
        return toMemberData(saved, roleRepository.findById(saved.getRoleId()).orElse(null));
    }

    public void removeMember(String storeId, String publicId) {
        StoreMember member = memberRepository.findByStoreIdAndPublicId(storeId, publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        StoreRole role = roleRepository.findById(member.getRoleId()).orElse(null);
        if (role != null && role.isLocked()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The store owner cannot be removed");
        }
        memberRepository.delete(member);
    }

    // --- Helpers ------------------------------------------------------------

    private Map<String, AccessLevel> permissionsForRole(Long roleId) {
        Map<String, AccessLevel> matrix = PermissionCatalog.uniformMatrix(AccessLevel.NONE);
        for (StoreRolePermission perm : permissionRepository.findByRoleId(roleId)) {
            matrix.put(perm.getPermissionKey(), perm.getAccessLevel());
        }
        return matrix;
    }

    private void savePermissions(Long roleId, Map<String, AccessLevel> matrix) {
        List<StoreRolePermission> rows = new ArrayList<>();
        for (Map.Entry<String, AccessLevel> entry : matrix.entrySet()) {
            StoreRolePermission perm = new StoreRolePermission();
            perm.setRoleId(roleId);
            perm.setPermissionKey(entry.getKey());
            perm.setAccessLevel(entry.getValue() == null ? AccessLevel.NONE : entry.getValue());
            rows.add(perm);
        }
        permissionRepository.saveAll(rows);
    }

    /** Overlay only the catalog keys present in {@code updates} onto {@code base}. */
    private void overlay(Map<String, AccessLevel> base, Map<String, AccessLevel> updates) {
        if (updates == null) {
            return;
        }
        for (Map.Entry<String, AccessLevel> entry : updates.entrySet()) {
            if (base.containsKey(entry.getKey()) && entry.getValue() != null) {
                base.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private StoreRole resolveRole(String storeId, String rolePublicId) {
        if (rolePublicId == null || rolePublicId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A role is required");
        }
        return roleRepository.findByStoreIdAndPublicId(storeId, rolePublicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role not found"));
    }

    private String uniqueRoleKey(String storeId, String name) {
        String base = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "role";
        }
        String candidate = base;
        int counter = 2;
        while (roleRepository.findByStoreIdAndRoleKey(storeId, candidate).isPresent()) {
            candidate = base + "-" + counter++;
        }
        return candidate;
    }

    private RoleData toRoleData(StoreRole role) {
        int memberCount = memberRepository.findByRoleId(role.getId()).size();
        return new RoleData(
                role.getPublicId(),
                role.getRoleKey(),
                role.getName(),
                role.getDescription(),
                role.isSystemRole(),
                role.isLocked(),
                memberCount,
                permissionsForRole(role.getId()));
    }

    private MemberData toMemberData(StoreMember member, StoreRole role) {
        return new MemberData(
                member.getPublicId(),
                member.getDisplayName(),
                member.getEmail(),
                member.getTitle(),
                role != null ? role.getPublicId() : null,
                role != null ? role.getRoleKey() : null,
                role != null ? role.getName() : null,
                member.getStatus(),
                member.getAdminUserId() != null,
                member.getInvitedAt(),
                member.getLastActiveAt());
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
