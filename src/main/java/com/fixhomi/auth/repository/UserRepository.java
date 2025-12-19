package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Find all active users by role.
     * Used for admin operations.
     *
     * @param role user role
     * @param isActive active status
     * @return list of users
     */
    List<User> findByRoleAndIsActive(Role role, Boolean isActive);
}
