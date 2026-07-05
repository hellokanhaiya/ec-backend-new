package com.ecommerce.access;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreMemberRepository extends JpaRepository<StoreMember, Long> {
    List<StoreMember> findByStoreIdOrderByCreatedAtAsc(String storeId);

    Optional<StoreMember> findByStoreIdAndPublicId(String storeId, String publicId);

    Optional<StoreMember> findByStoreIdAndAdminUserId(String storeId, Long adminUserId);

    Optional<StoreMember> findByStoreIdAndEmailIgnoreCase(String storeId, String email);

    List<StoreMember> findByAdminUserId(Long adminUserId);

    List<StoreMember> findByEmailIgnoreCaseAndStatus(String email, MemberStatus status);

    List<StoreMember> findByRoleId(Long roleId);

    boolean existsByRoleIdAndStatusNot(Long roleId, MemberStatus status);
}
