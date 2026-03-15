package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.AdminCreateUserRequest;
import com.fixhomi.auth.dto.ChangePasswordRequest;
import com.fixhomi.auth.dto.DeleteAccountRequest;
import com.fixhomi.auth.dto.MessageResponse;
import com.fixhomi.auth.dto.UpdateProfileRequest;
import com.fixhomi.auth.dto.UpdateUserStatusRequest;
import com.fixhomi.auth.dto.UserProfileResponse;
import com.fixhomi.auth.entity.DeleteAccountOtp;
import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.DuplicateResourceException;
import com.fixhomi.auth.exception.InvalidPasswordException;
import com.fixhomi.auth.exception.InvalidRoleException;
import com.fixhomi.auth.exception.ResourceNotFoundException;
import com.fixhomi.auth.repository.DeleteAccountOtpRepository;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.service.notification.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Service for user account lifecycle operations.
 * Uses database-backed OTP storage (survives server restarts).
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeleteAccountOtpRepository deleteAccountOtpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private SmsService smsService;

    @Value("${fixhomi.notification.sms.msg91.delete-template-id:}")
    private String deleteTemplateId;

    @Value("${fixhomi.verification.delete-otp.expiration-minutes:5}")
    private int deleteOtpExpirationMinutes;

    @Value("${fixhomi.verification.delete-otp.max-attempts:3}")
    private int deleteOtpMaxAttempts;

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

    @Transactional
    public MessageResponse changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        boolean hasExistingPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();

        if (hasExistingPassword) {
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                throw new InvalidPasswordException("Current password is required");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
                throw new InvalidPasswordException("Current password is incorrect");
            }
            if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
                throw new InvalidPasswordException("New password must be different from current password");
            }
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        String action = hasExistingPassword ? "changed" : "set";
        logger.info("Password {} successfully for user: {}", action, email);

        return new MessageResponse("Password " + action + " successfully");
    }

    @Transactional
    public UserProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        final User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
            logger.debug("Updating fullName for user: {}", email);
        }

        if (request.getPhoneNumber() != null) {
            String normalizedNew = User.normalizePhoneNumber(request.getPhoneNumber());
            String currentPhone = user.getPhoneNumber(); // already normalized in DB
            boolean phoneIsChanging = normalizedNew != null && !normalizedNew.isBlank()
                    && (currentPhone == null || !currentPhone.equals(normalizedNew));

            if (normalizedNew != null && !normalizedNew.isBlank()) {
                // Only block if another active user has this phone VERIFIED
                if (!normalizedNew.equals(user.getPhoneNumber())) {
                    if (userRepository.existsByPhoneNumberAndIsPhoneVerifiedTrueAndIsActiveTrue(normalizedNew)) {
                        throw new DuplicateResourceException("User", "phoneNumber", request.getPhoneNumber());
                    }
                    // Clear unverified phone from old owner so this user can claim it
                    final Long currentUserId = user.getId();
                    userRepository.findByPhoneNumberAndIsActiveTrue(normalizedNew).ifPresent(oldUser -> {
                        if (!oldUser.getId().equals(currentUserId) && !Boolean.TRUE.equals(oldUser.getIsPhoneVerified())) {
                            logger.info("Clearing unverified phone {} from user {} for profile update",
                                    normalizedNew, oldUser.getId());
                            oldUser.setPhoneNumber(null);
                            oldUser.setIsPhoneVerified(false);
                            userRepository.save(oldUser);
                        }
                    });
                }
                user.setPhoneNumber(normalizedNew);
            } else {
                user.setPhoneNumber(null);
            }

            if (phoneIsChanging) {
                user.setIsPhoneVerified(false);
                logger.info("Phone number changed for user: {} — phone verification reset", email);
            }

            logger.debug("Updating phoneNumber for user: {}", email);
        }

        User savedUser = userRepository.save(user);
        logger.info("Profile updated successfully for user: {}", email);

        return new UserProfileResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getPhoneNumber(),
                savedUser.getFullName(),
                savedUser.getRole(),
                savedUser.getIsActive(),
                savedUser.getIsEmailVerified(),
                savedUser.getIsPhoneVerified(),
                savedUser.getCreatedAt(),
                savedUser.getUpdatedAt(),
                savedUser.getLastLoginAt(),
                savedUser.getPasswordHash() != null && !savedUser.getPasswordHash().isBlank()
        );
    }

    @Transactional
    public UserProfileResponse updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        user.setIsActive(request.getIsActive());
        user = userRepository.save(user);

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

    @Transactional
    public UserProfileResponse createUserByAdmin(AdminCreateUserRequest request) {
        logger.debug("Admin creating user with email: {}", request.getEmail());

        Role requestedRole = request.getRole();
        if (requestedRole != Role.ADMIN && requestedRole != Role.IT_ADMIN && requestedRole != Role.SUPPORT) {
            throw new InvalidRoleException(requestedRole.name(),
                    "Admin user creation only allows ADMIN, IT_ADMIN, or SUPPORT roles. Use public registration for USER/SERVICE_PROVIDER.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        String normalizedAdminPhone = User.normalizePhoneNumber(request.getPhoneNumber());
        if (normalizedAdminPhone != null && !normalizedAdminPhone.isBlank()) {
            if (userRepository.existsByPhoneNumberAndIsPhoneVerifiedTrueAndIsActiveTrue(normalizedAdminPhone)) {
                throw new DuplicateResourceException("User", "phoneNumber", request.getPhoneNumber());
            }
            // Clear unverified phone from old owner
            userRepository.findByPhoneNumberAndIsActiveTrue(normalizedAdminPhone).ifPresent(oldUser -> {
                if (!Boolean.TRUE.equals(oldUser.getIsPhoneVerified())) {
                    oldUser.setPhoneNumber(null);
                    oldUser.setIsPhoneVerified(false);
                    userRepository.save(oldUser);
                }
            });
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPhoneNumber(normalizedAdminPhone);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRole(request.getRole());
        user.setIsActive(true);
        user.setIsEmailVerified(true);
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
                true
        );
    }

    /**
     * Request OTP for account deletion.
     * OTP is generated locally, persisted to database, and sent via SMS.
     */
    @Transactional
    public String requestDeleteAccountOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        String phoneNumber = user.getPhoneNumber();
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new InvalidPasswordException("No phone number registered. Please add a phone number first.");
        }

        if (!Boolean.TRUE.equals(user.getIsPhoneVerified())) {
            throw new InvalidPasswordException("Phone number is not verified. Please verify your phone first.");
        }

        // Invalidate any existing OTPs for this phone
        deleteAccountOtpRepository.invalidateAllOtpsForPhone(phoneNumber);

        // Generate and persist OTP
        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(deleteOtpExpirationMinutes);
        DeleteAccountOtp otpEntity = new DeleteAccountOtp(user.getId(), phoneNumber, otp, expiresAt);
        deleteAccountOtpRepository.save(otpEntity);

        // Send OTP via SMS
        boolean sent = smsService.sendOtp(phoneNumber, otp, deleteTemplateId);
        if (!sent) {
            otpEntity.markAsUsed();
            deleteAccountOtpRepository.save(otpEntity);
            throw new RuntimeException("Failed to send OTP. Please try again later.");
        }

        logger.info("Delete account OTP sent for user: {} to phone: {}",
                email, maskPhoneNumber(phoneNumber));

        return maskPhoneNumber(phoneNumber);
    }

    /**
     * Delete user account with OTP verification.
     * OTP is verified from database.
     */
    @Transactional
    public MessageResponse deleteAccountWithOtp(String email, DeleteAccountRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        String phoneNumber = user.getPhoneNumber();
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new InvalidPasswordException("No phone number registered.");
        }

        // Verify OTP from database
        DeleteAccountOtp otpEntry = deleteAccountOtpRepository
                .findLatestValidOtp(phoneNumber, LocalDateTime.now())
                .orElse(null);

        if (otpEntry == null) {
            throw new InvalidPasswordException("No pending OTP. Please request a new code.");
        }

        if (otpEntry.isExpired()) {
            otpEntry.markAsUsed();
            deleteAccountOtpRepository.save(otpEntry);
            throw new InvalidPasswordException("OTP has expired. Please request a new one.");
        }

        otpEntry.incrementAttempts();
        if (otpEntry.getAttempts() > deleteOtpMaxAttempts) {
            otpEntry.markAsUsed();
            deleteAccountOtpRepository.save(otpEntry);
            throw new InvalidPasswordException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        if (!MessageDigest.isEqual(otpEntry.getOtp().getBytes(StandardCharsets.UTF_8),
                request.getOtp().getBytes(StandardCharsets.UTF_8))) {
            deleteAccountOtpRepository.save(otpEntry);
            logger.warn("Invalid OTP attempt for account deletion. User: {}", email);
            throw new InvalidPasswordException("Invalid or expired OTP. Please request a new code.");
        }

        // OTP verified — mark as used
        otpEntry.markAsUsed();
        deleteAccountOtpRepository.save(otpEntry);

        // Perform soft delete
        String reason = request.getReason() != null ? request.getReason() : "User requested deletion";
        performSoftDelete(user, reason);

        logger.info("Account deleted for user: {} (ID: {}) - OTP verified", email, user.getId());

        return new MessageResponse("Account deleted successfully. We're sorry to see you go.");
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "******";
        }
        String lastFour = phoneNumber.substring(phoneNumber.length() - 4);
        return "******" + lastFour;
    }

    public boolean isUserAuthorizedForDeletion(String currentEmail, Long targetUserId) {
        User currentUser = userRepository.findByEmail(currentEmail).orElse(null);
        if (currentUser == null) return false;
        if (currentUser.getId().equals(targetUserId)) return true;
        return currentUser.getRole() == Role.ADMIN;
    }

    @Transactional
    public MessageResponse deleteAccountById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        performSoftDelete(user, "Deleted via service call");
        logger.info("Account deleted for user ID: {}", userId);

        return new MessageResponse("Account deleted successfully");
    }

    private void performSoftDelete(User user, String reason) {
        int revokedCount = refreshTokenService.revokeAllUserTokens(user.getId());
        logger.debug("Revoked {} refresh tokens for deleted user: {}", revokedCount, user.getId());

        String originalEmail = user.getEmail();
        String originalPhone = user.getPhoneNumber();

        user.setEmail("deleted_" + user.getId() + "@del.local");

        if (originalPhone != null && !originalPhone.isBlank()) {
            user.setPhoneNumber("del_" + user.getId());
        }

        logger.info("Anonymizing user PII - Email: {} -> {}, Phone: {} -> {}",
            originalEmail, user.getEmail(),
            maskPhone(originalPhone), user.getPhoneNumber());

        user.setIsActive(false);
        user.setFullName("[Deleted User]");
        user.setPasswordHash(null);

        userRepository.save(user);
        logger.info("Soft deleted user account (ID: {}). Reason: {}", user.getId(), reason);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }

    private String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            otp.append(SECURE_RANDOM.nextInt(10));
        }
        return otp.toString();
    }

    /**
     * Cleanup expired delete account OTP entries every 10 minutes.
     */
    @Scheduled(fixedRate = 600000)
    @Transactional
    public void cleanupExpiredDeleteOtps() {
        int removed = deleteAccountOtpRepository.deleteExpiredOtps(LocalDateTime.now());
        if (removed > 0) {
            logger.info("Cleaned up {} expired delete account OTP entries", removed);
        }
    }
}
