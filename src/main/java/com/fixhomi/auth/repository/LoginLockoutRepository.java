package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.LoginLockout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoginLockoutRepository extends JpaRepository<LoginLockout, Long> {

    Optional<LoginLockout> findByIdentifier(String identifier);

    /** Find lockout by userId (for cross-method lockout) */
    List<LoginLockout> findByUserId(Long userId);

    /** Check if any identifier for this user is currently locked */
    @Query("SELECT l FROM LoginLockout l WHERE l.userId = :userId AND l.lockedUntil > :now")
    List<LoginLockout> findActiveLocksForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** Delete expired lockout entries (cleanup) */
    @Modifying
    @Query("DELETE FROM LoginLockout l WHERE l.lockedUntil IS NOT NULL AND l.lockedUntil < :cutoff " +
           "OR (l.lockedUntil IS NULL AND l.lastAttemptAt < :cutoff)")
    int deleteExpiredLockouts(@Param("cutoff") LocalDateTime cutoff);
}
