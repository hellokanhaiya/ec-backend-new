package com.ecommerce.access;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRolePermissionRepository extends JpaRepository<StoreRolePermission, Long> {
    List<StoreRolePermission> findByRoleId(Long roleId);

    List<StoreRolePermission> findByRoleIdIn(List<Long> roleIds);

    void deleteByRoleId(Long roleId);
}
