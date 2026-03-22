package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email address.
     * Used during login authentication.
     *
     * @param email user's email
     * @return Optional containing user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by phone number.
     * Used for phone-based authentication.
     *
     * @param phoneNumber user's phone number
     * @return Optional containing user if found
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Check if email already exists.
     * Used during registration.
     *
     * @param email email to check
     * @return true if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Check if phone number already exists.
     * Used during registration.
     *
     * @param phoneNumber phone number to check
     * @return true if phone exists
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Check if phone number exists AND is verified on an active account.
     * Only verified phones should block new registrations/updates.
     */
    boolean existsByPhoneNumberAndIsPhoneVerifiedTrueAndIsActiveTrue(String phoneNumber);

    /**
     * Find user by phone number who is active (for clearing unverified phones).
     */
    Optional<User> findByPhoneNumberAndIsActiveTrue(String phoneNumber);

    /**
     * Check if email exists AND is verified on an active account.
     * Only verified emails should block new registrations.
     */
    boolean existsByEmailAndIsEmailVerifiedTrueAndIsActiveTrue(String email);

    /**
     * Find user by email who is active (for clearing unverified emails).
     */
    Optional<User> findByEmailAndIsActiveTrue(String email);

    /**
     * Find all active users by role.
     * Used for admin operations.
     *
     * @param role user role
     * @param isActive active status
     * @return list of users
     */
    List<User> findByRoleAndIsActive(Role role, Boolean isActive);

    /**
     * Find user by Apple user ID (stable per-app identifier from Apple Sign-In).
     */
    Optional<User> findByAppleUserId(String appleUserId);

    /**
     * Find users by roles with pagination.
     */
    Page<User> findByRoleIn(List<Role> roles, Pageable pageable);

    /**
     * Find users by roles and active status with pagination.
     */
    Page<User> findByRoleInAndIsActive(List<Role> roles, Boolean isActive, Pageable pageable);

    /**
     * Search users by roles and query (name, email, or phone) with pagination.
     */
    @Query("SELECT u FROM User u WHERE u.role IN :roles AND (" +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "u.phoneNumber LIKE CONCAT('%', :search, '%'))")
    Page<User> searchByRolesAndQuery(@Param("roles") List<Role> roles, @Param("search") String search, Pageable pageable);

    /**
     * Search users by roles, query, and active status with pagination.
     */
    @Query("SELECT u FROM User u WHERE u.role IN :roles AND u.isActive = :isActive AND (" +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "u.phoneNumber LIKE CONCAT('%', :search, '%'))")
    Page<User> searchByRolesAndQueryAndStatus(@Param("roles") List<Role> roles, @Param("search") String search, @Param("isActive") Boolean isActive, Pageable pageable);
}
