package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.SupportPermission;
import com.fixhomi.auth.entity.SupportPermissionGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportPermissionGrantRepository extends JpaRepository<SupportPermissionGrant, Long> {

    boolean existsByUserIdAndPermission(Long userId, SupportPermission permission);

    List<SupportPermissionGrant> findByUserId(Long userId);

    void deleteByUserIdAndPermission(Long userId, SupportPermission permission);
}