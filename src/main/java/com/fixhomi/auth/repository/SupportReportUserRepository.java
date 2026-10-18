package com.fixhomi.auth.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.User;

/**
 * Read-only queries for the daily report.
 *
 * It extends the small marker interface Repository (not JpaRepository), so it only has the
 * methods written below, and the existing UserRepository does not need to be touched.
 *
 * "dayStart" is the first moment of the chosen day; "dayEnd" is the first moment of the NEXT
 * day. We use  >= dayStart  and  < dayEnd  so the whole day is covered exactly once.
 */
@org.springframework.stereotype.Repository
public interface SupportReportUserRepository extends Repository<User, Long> {

    /** People who signed up on that day, for the given roles (USER / SERVICE_PROVIDER). */
    @Query("SELECT u FROM User u WHERE u.role IN :roles "
            + "AND u.createdAt >= :dayStart AND u.createdAt < :dayEnd ORDER BY u.createdAt ASC")
    List<User> findRegisteredBetween(@Param("roles") List<Role> roles,
                                     @Param("dayStart") LocalDateTime dayStart,
                                     @Param("dayEnd") LocalDateTime dayEnd);

    /** People (of those roles) whose most recent login was on that day. */
    @Query("SELECT u FROM User u WHERE u.role IN :roles "
            + "AND u.lastLoginAt >= :dayStart AND u.lastLoginAt < :dayEnd ORDER BY u.lastLoginAt ASC")
    List<User> findLastLoginBetween(@Param("roles") List<Role> roles,
                                    @Param("dayStart") LocalDateTime dayStart,
                                    @Param("dayEnd") LocalDateTime dayEnd);

    /** Used by the dashboard. */
    long countByRole(Role role);
}