package com.ecommerce.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecommerce.auth.repository.AdminAuthUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(
        properties = {
            "spring.config.import=",
            "spring.datasource.url=jdbc:h2:mem:access;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password="
        })
class StoreAccessServiceTest {
    private static final String STORE = "store-1";
    private static final Long OWNER_ID = 1L;

    @Autowired private StoreRoleRepository roleRepository;
    @Autowired private StoreRolePermissionRepository permissionRepository;
    @Autowired private StoreMemberRepository memberRepository;

    private AdminAuthUserRepository adminAuthUserRepository;
    private StoreAccessService service;

    @BeforeEach
    void setUp() {
        adminAuthUserRepository = mock(AdminAuthUserRepository.class);
        when(adminAuthUserRepository.findByEmailIgnoreCase(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
        service = new StoreAccessService(
                roleRepository, permissionRepository, memberRepository, adminAuthUserRepository);
    }

    @Test
    void ensureDefaultsSeedsFiveRolesAndOwnerMemberIdempotently() {
        service.ensureDefaultsForStore(STORE, OWNER_ID, "owner@store.com", "Owner");
        service.ensureDefaultsForStore(STORE, OWNER_ID, "owner@store.com", "Owner");

        assertThat(roleRepository.findByStoreIdOrderByCreatedAtAsc(STORE)).hasSize(12);
        assertThat(memberRepository.findByStoreIdOrderByCreatedAtAsc(STORE)).hasSize(1);
        assertThat(memberRepository.findByStoreIdAndAdminUserId(STORE, OWNER_ID)).isPresent();
    }

    @Test
    void ownerResolvesToFullManage() {
        service.ensureDefaultsForStore(STORE, OWNER_ID, "owner@store.com", "Owner");

        StoreAccessData access = service.resolveAccess(STORE, OWNER_ID, true);

        assertThat(access.isOwner()).isTrue();
        assertThat(access.roleKey()).isEqualTo(PermissionCatalog.ROLE_OWNER);
        assertThat(access.permissions().get(PermissionCatalog.SETTINGS_USERS)).isEqualTo(AccessLevel.MANAGE);
        assertThat(access.permissions().get(PermissionCatalog.ORDERS_ALL)).isEqualTo(AccessLevel.MANAGE);
    }

    @Test
    void supportMemberResolvesToSupportMatrix() {
        service.ensureDefaultsForStore(STORE, OWNER_ID, "owner@store.com", "Owner");
        StoreRole support = roleRepository.findByStoreIdAndRoleKey(STORE, PermissionCatalog.ROLE_SUPPORT).orElseThrow();
        Long staffId = 42L;
        StoreMember member = new StoreMember();
        member.setStoreId(STORE);
        member.setAdminUserId(staffId);
        member.setEmail("support@store.com");
        member.setRoleId(support.getId());
        member.setStatus(MemberStatus.ACTIVE);
        memberRepository.save(member);

        StoreAccessData access = service.resolveAccess(STORE, staffId, false);

        assertThat(access.isOwner()).isFalse();
        assertThat(access.roleKey()).isEqualTo(PermissionCatalog.ROLE_SUPPORT);
        assertThat(access.permissions().get(PermissionCatalog.ORDERS_ALL)).isEqualTo(AccessLevel.VIEW);
        assertThat(access.permissions().get(PermissionCatalog.PRODUCTS_LIST)).isEqualTo(AccessLevel.NONE);
        assertThat(access.permissions().get(PermissionCatalog.SETTINGS_USERS)).isEqualTo(AccessLevel.NONE);
    }

    @Test
    void pausedMemberGetsNoAccess() {
        service.ensureDefaultsForStore(STORE, OWNER_ID, "owner@store.com", "Owner");
        StoreRole manager = roleRepository.findByStoreIdAndRoleKey(STORE, PermissionCatalog.ROLE_MANAGER).orElseThrow();
        Long staffId = 77L;
        StoreMember member = new StoreMember();
        member.setStoreId(STORE);
        member.setAdminUserId(staffId);
        member.setRoleId(manager.getId());
        member.setStatus(MemberStatus.PAUSED);
        memberRepository.save(member);

        StoreAccessData access = service.resolveAccess(STORE, staffId, false);

        assertThat(access.status()).isEqualTo(MemberStatus.PAUSED);
        assertThat(access.permissions()).isEmpty();
    }

    @Test
    void inviteThenLinkOnSignInActivatesMember() {
        service.ensureDefaultsForStore(STORE, OWNER_ID, "owner@store.com", "Owner");
        StoreRole support = roleRepository.findByStoreIdAndRoleKey(STORE, PermissionCatalog.ROLE_SUPPORT).orElseThrow();

        MemberData invited = service.inviteMember(
                STORE, new MemberInviteRequest("new@staff.com", "New Staff", "Agent", support.getPublicId()));
        assertThat(invited.status()).isEqualTo(MemberStatus.INVITED);
        assertThat(invited.linked()).isFalse();

        service.linkPendingInvites(555L, "new@staff.com");

        StoreMember linked = memberRepository.findByStoreIdAndEmailIgnoreCase(STORE, "new@staff.com").orElseThrow();
        assertThat(linked.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(linked.getAdminUserId()).isEqualTo(555L);
    }

    @Test
    void updateRolePersistsMatrixAndOwnerRoleIsLocked() {
        service.ensureDefaultsForStore(STORE, OWNER_ID, "owner@store.com", "Owner");
        StoreRole manager = roleRepository.findByStoreIdAndRoleKey(STORE, PermissionCatalog.ROLE_MANAGER).orElseThrow();

        service.updateRole(STORE, manager.getPublicId(),
                new RoleRequest(null, null, java.util.Map.of(PermissionCatalog.ORDERS_ABANDONED, AccessLevel.MANAGE)));
        StoreAccessData access = memberMatrixForRole(manager);
        assertThat(access.permissions().get(PermissionCatalog.ORDERS_ABANDONED)).isEqualTo(AccessLevel.MANAGE);

        StoreRole owner = roleRepository.findByStoreIdAndRoleKey(STORE, PermissionCatalog.ROLE_OWNER).orElseThrow();
        assertThatThrownBy(() -> service.updateRole(STORE, owner.getPublicId(),
                new RoleRequest("Hacked", null, null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    /** Resolve a role's matrix by attaching a throwaway active member to it. */
    private StoreAccessData memberMatrixForRole(StoreRole role) {
        Long probeId = 9999L;
        StoreMember probe = memberRepository.findByStoreIdAndAdminUserId(STORE, probeId).orElseGet(StoreMember::new);
        probe.setStoreId(STORE);
        probe.setAdminUserId(probeId);
        probe.setRoleId(role.getId());
        probe.setStatus(MemberStatus.ACTIVE);
        memberRepository.save(probe);
        return service.resolveAccess(STORE, probeId, false);
    }
}
