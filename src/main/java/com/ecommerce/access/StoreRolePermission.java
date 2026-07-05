package com.ecommerce.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One page grant for a role: the access level a role has on a single permission key. */
@Getter
@Setter
@Entity
@Table(name = "store_role_permissions", indexes = {@Index(name = "idx_role_perms_role", columnList = "role_id")})
public class StoreRolePermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "permission_key", nullable = false, length = 64)
    private String permissionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 16)
    private AccessLevel accessLevel;
}
