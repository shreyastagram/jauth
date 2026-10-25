package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.SupportPermissionGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SupportPermissionGrantRepository extends JpaRepository<SupportPermissionGrant, Long> {

    boolean existsByUserIdAndPermission(Long userId, String permission);

    Optional<SupportPermissionGrant> findByUserIdAndPermission(Long userId, String permission);

    List<SupportPermissionGrant> findByUserId(Long userId);
}
