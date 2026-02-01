package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.AdminCreateUserRequest;
import com.fixhomi.auth.dto.ChangePasswordRequest;
import com.fixhomi.auth.dto.DeleteAccountRequest;
import com.fixhomi.auth.dto.MessageResponse;
import com.fixhomi.auth.dto.UpdateProfileRequest;
import com.fixhomi.auth.dto.UpdateUserStatusRequest;
import com.fixhomi.auth.dto.UserProfileResponse;
import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.DuplicateResourceException;
import com.fixhomi.auth.exception.InvalidPasswordException;
import com.fixhomi.auth.exception.InvalidRoleException;
import com.fixhomi.auth.exception.ResourceNotFoundException;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.service.notification.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    @Autowired
    private SmsService smsService;

    /**
     * Get user profile by email (from JWT).
     * Returns complete user profile including verification status.
     *
     * @param email user's email from JWT token
     * @return user profile response with all fields
     */
    public UserProfileResponse getUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getFullName(),
                user.getRole(),
                user.getIsActive(),
                user.getIsEmailVerified(),
                user.getIsPhoneVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt(),
                user.getPasswordHash() != null && !user.getPasswordHash().isBlank()
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

        // Check if user has an existing password (not OAuth-only)
        boolean hasExistingPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
        
        if (hasExistingPassword) {
            // Validate current password
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                throw new InvalidPasswordException("Current password is required");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
                throw new InvalidPasswordException("Current password is incorrect");
            }
            // Check if new password is same as old password
            if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
                throw new InvalidPasswordException("New password must be different from current password");
            }
        }
        // For OAuth-only users, currentPassword is optional (setting first password)

        // Hash and save new password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        String action = hasExistingPassword ? "changed" : "set";
        logger.info("Password {} successfully for user: {}", action, email);

        return new MessageResponse("Password " + action + " successfully");
    }

    // /**
    //  * Update user profile (name, phone).
    //  * Allows authenticated users to update their own profile.
    //  *
    //  * @param email user's email from JWT token
    //  * @param request update profile request
    //  * @return updated user profile
    //  */
    @Transactional
    public UserProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        // Update full name if provided
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
            logger.debug("Updating fullName for user: {}", email);
        }

        // Update phone number if provided
        if (request.getPhoneNumber() != null) {
            // Check if phone number is already used by another user
            if (!request.getPhoneNumber().isBlank()) {
                boolean phoneExists = userRepository.existsByPhoneNumber(request.getPhoneNumber());
                if (phoneExists && !request.getPhoneNumber().equals(user.getPhoneNumber())) {
                    throw new DuplicateResourceException("User", "phoneNumber", request.getPhoneNumber());
                }
                user.setPhoneNumber(request.getPhoneNumber().trim());
            } else {
                user.setPhoneNumber(null); // Allow clearing phone number
            }
            logger.debug("Updating phoneNumber for user: {}", email);
        }

        user = userRepository.save(user);

        logger.info("Profile updated successfully for user: {}", email);

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getFullName(),
                user.getRole(),
                user.getIsActive(),
                user.getIsEmailVerified(),
                user.getIsPhoneVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt(),
                user.getPasswordHash() != null && !user.getPasswordHash().isBlank()
        );
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
                user.getPhoneNumber(),
                user.getFullName(),
                user.getRole(),
                user.getIsActive(),
                user.getIsEmailVerified(),
                user.getIsPhoneVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt(),
                user.getPasswordHash() != null && !user.getPasswordHash().isBlank()
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
                user.getPhoneNumber(),
                user.getFullName(),
                user.getRole(),
                user.getIsActive(),
                user.getIsEmailVerified(),
                user.getIsPhoneVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt(),
                true  // Admin-created users always have a password
        );
    }

    /**
     * Request OTP for account deletion.
     * Sends OTP to user's verified phone number.
     *
     * @param email user's email from JWT token
     * @return masked phone number where OTP was sent
     * @throws InvalidPasswordException if phone is not verified
     */
    @Transactional(readOnly = true)
    public String requestDeleteAccountOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        // Validate phone number exists and is verified
        String phoneNumber = user.getPhoneNumber();
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new InvalidPasswordException("No phone number registered. Please add a phone number first.");
        }

        if (!Boolean.TRUE.equals(user.getIsPhoneVerified())) {
            throw new InvalidPasswordException("Phone number is not verified. Please verify your phone first.");
        }

        // Send OTP via Twilio Verify API
        boolean sent = smsService.startVerification(phoneNumber);
        if (!sent) {
            throw new RuntimeException("Failed to send OTP. Please try again later.");
        }

        logger.info("📱 Delete account OTP sent for user: {} to phone: {}", 
                email, maskPhoneNumber(phoneNumber));

        return maskPhoneNumber(phoneNumber);
    }

    /**
     * Delete user account with OTP verification (production-grade).
     * Verifies OTP before performing soft delete.
     *
     * @param email user's email from JWT token
     * @param request delete account request with OTP
     * @return success message
     */
    @Transactional
    public MessageResponse deleteAccountWithOtp(String email, DeleteAccountRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        // Validate phone number
        String phoneNumber = user.getPhoneNumber();
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new InvalidPasswordException("No phone number registered.");
        }

        // Verify OTP via Twilio Verify API
        boolean otpValid = smsService.checkVerification(phoneNumber, request.getOtp());
        if (!otpValid) {
            logger.warn("❌ Invalid OTP attempt for account deletion. User: {}", email);
            throw new InvalidPasswordException("Invalid or expired OTP. Please request a new code.");
        }

        // OTP verified - perform soft delete
        String reason = request.getReason() != null ? request.getReason() : "User requested deletion";
        performSoftDelete(user, reason);

        logger.info("🗑️ Account deleted for user: {} (ID: {}) - OTP verified", email, user.getId());

        return new MessageResponse("Account deleted successfully. We're sorry to see you go.");
    }

    /**
     * Mask phone number for privacy (show only last 4 digits).
     * Example: +1234567890 -> ******7890
     *
     * @param phoneNumber full phone number
     * @return masked phone number
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "******";
        }
        String lastFour = phoneNumber.substring(phoneNumber.length() - 4);
        return "******" + lastFour;
    }

    /**
     * Delete user account by ID (for service-to-service calls).
     *
     * @param userId user ID to delete
     * @return success message
     */
    @Transactional
    public MessageResponse deleteAccountById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Soft delete
        performSoftDelete(user, "Deleted via service call");

        logger.info("🗑️ Account deleted for user ID: {}", userId);

        return new MessageResponse("Account deleted successfully");
    }

    /**
     * Perform soft delete on user account.
     * - Disables the account
     * - Anonymizes email and phone (to allow re-registration)
     * - Revokes all refresh tokens
     *
     * @param user the user to delete
     * @param reason optional reason for deletion
     */
    private void performSoftDelete(User user, String reason) {
        // Revoke all refresh tokens
        int revokedCount = refreshTokenService.revokeAllUserTokens(user.getId());
        logger.debug("Revoked {} refresh tokens for deleted user: {}", revokedCount, user.getId());

        // Anonymize PII to allow email/phone reuse
        // Use short format to fit within database column limits (email: 255, phone: 20)
        String originalEmail = user.getEmail();
        String originalPhone = user.getPhoneNumber();
        
        // Email: deleted_<userId>@del.local (plenty of space in varchar 255)
        user.setEmail("deleted_" + user.getId() + "@del.local");
        
        // Phone: del_<userId> format to fit varchar(20) limit
        // e.g., "del_66" = 6 chars, "del_99999" = 9 chars - safe for any reasonable userId
        if (originalPhone != null && !originalPhone.isBlank()) {
            user.setPhoneNumber("del_" + user.getId());
        }
        
        logger.info("Anonymizing user PII - Email: {} -> {}, Phone: {} -> {}", 
            originalEmail, user.getEmail(), 
            maskPhone(originalPhone), user.getPhoneNumber());

        // Disable account
        user.setIsActive(false);
        user.setFullName("[Deleted User]");
        
        // Clear sensitive data
        user.setPasswordHash(null);

        userRepository.save(user);

        logger.info("✅ Soft deleted user account (ID: {}). Reason: {}", user.getId(), reason);
    }
    
    /**
     * Mask phone number for logging (show last 4 digits only)
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }
}
