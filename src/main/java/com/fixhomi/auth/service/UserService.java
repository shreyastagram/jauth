package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.AdminCreateUserRequest;
import com.fixhomi.auth.dto.ChangePasswordRequest;
import com.fixhomi.auth.dto.MessageResponse;
import com.fixhomi.auth.dto.UpdateUserStatusRequest;
import com.fixhomi.auth.dto.UserProfileResponse;
import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.DuplicateResourceException;
import com.fixhomi.auth.exception.InvalidPasswordException;
import com.fixhomi.auth.exception.InvalidRoleException;
import com.fixhomi.auth.exception.ResourceNotFoundException;
import com.fixhomi.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user account lifecycle operations.
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenService refreshTokenService;

    /**
     * Get user profile by email (from JWT).
     *
     * @param email user's email from JWT token
     * @return user profile response
     */
    public UserProfileResponse getUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getIsActive(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }

    /**
     * Change password for authenticated user.
     *
     * @param email user's email from JWT token
     * @param request change password request
     * @return success message
     */
    @Transactional
    public MessageResponse changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        // Validate current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException("Current password is incorrect");
        }

        // Check if new password is same as old password
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException("New password must be different from current password");
        }

        // Hash and save new password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        logger.info("Password changed successfully for user: {}", email);

        return new MessageResponse("Password changed successfully");
    }

    /**
     * Update user account status (Admin only).
     *
     * @param userId user ID to update
     * @param request status update request
     * @return updated user profile
     */
    @Transactional
    public UserProfileResponse updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        user.setIsActive(request.getIsActive());
        user = userRepository.save(user);

        // Revoke all refresh tokens when user is disabled
        if (!request.getIsActive()) {
            int revokedCount = refreshTokenService.revokeAllUserTokens(userId);
            logger.info("Revoked {} refresh tokens for disabled user: {}", revokedCount, userId);
        }

        String action = request.getIsActive() ? "enabled" : "disabled";
        logger.info("User account {} for userId: {}", action, userId);

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getIsActive(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }

    /**
     * Create a user with elevated role (Admin only).
     * Only ADMIN, IT_ADMIN, and SUPPORT roles can be created via this method.
     *
     * @param request admin create user request
     * @return created user profile
     */
    @Transactional
    public UserProfileResponse createUserByAdmin(AdminCreateUserRequest request) {
        logger.debug("Admin creating user with email: {}", request.getEmail());

        // Validate role - only elevated roles allowed via admin creation
        Role requestedRole = request.getRole();
        if (requestedRole != Role.ADMIN && requestedRole != Role.IT_ADMIN && requestedRole != Role.SUPPORT) {
            throw new InvalidRoleException(requestedRole.name(), 
                    "Admin user creation only allows ADMIN, IT_ADMIN, or SUPPORT roles. Use public registration for USER/SERVICE_PROVIDER.");
        }

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        // Check if phone number already exists (if provided)
        if (request.getPhoneNumber() != null && 
            !request.getPhoneNumber().isBlank() &&
            userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new DuplicateResourceException("User", "phoneNumber", request.getPhoneNumber());
        }

        // Create new user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRole(request.getRole());
        user.setIsActive(true);
        user.setIsEmailVerified(true); // Admin-created users are verified
        user.setIsPhoneVerified(false);

        user = userRepository.save(user);

        logger.info("Admin created user: {} (role: {})", user.getEmail(), user.getRole());

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getIsActive(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}
