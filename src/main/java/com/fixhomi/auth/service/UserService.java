package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.AdminCreateUserRequest;
import com.fixhomi.auth.dto.AdminUserListResponse;
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
import com.fixhomi.auth.exception.VerificationException;
import com.fixhomi.auth.repository.DeleteAccountOtpRepository;
import com.fixhomi.auth.repository.LoginLockoutRepository;
import com.fixhomi.auth.repository.RefreshTokenRepository;
import com.fixhomi.auth.repository.TrustedDeviceRepository;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.repository.UserSessionRepository;
import com.fixhomi.auth.service.notification.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TrustedDeviceRepository trustedDeviceRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private LoginLockoutRepository loginLockoutRepository;

    @Autowired
    private SmsService smsService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Value("${fixhomi.notification.sms.msg91.delete-template-id:}")
    private String deleteTemplateId;

    @Value("${fixhomi.verification.delete-otp.expiration-minutes:5}")
    private int deleteOtpExpirationMinutes;

    @Value("${fixhomi.verification.delete-otp.max-attempts:3}")
    private int deleteOtpMaxAttempts;

    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

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
    public MessageResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

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
        logger.info("Password {} successfully for user: userId={}", action, userId);

        return new MessageResponse("Password " + action + " successfully");
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
            logger.debug("Updating fullName for user: userId={}", userId);
        }

        if (request.getPhoneNumber() != null) {
            // Phone changes are NOT allowed through the plain profile update.
            // The verify-then-replace flow (POST /api/users/phone/change/*) is
            // the only writer: the number and its verified flag change together,
            // atomically, only after the NEW number passes OTP. Ignoring (rather
            // than rejecting) keeps older app builds working for their other
            // profile fields — their phone edit becomes a visible no-op.
            logger.info("Ignoring phoneNumber in profile update for user: userId={} — phone changes require the OTP change flow", userId);
        }

        User savedUser = userRepository.save(user);
        logger.info("Profile updated successfully for user: userId={}", userId);

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

    /**
     * Set or change a USER's email after signup.
     *
     * <p>Phone-only users add an email here; any user may change theirs. In every
     * case the email is stored UNVERIFIED and a fresh verification link is sent —
     * so changing an already-verified email re-triggers verification. Email is
     * unique across all ACTIVE users (strict — matches the Mongo sparse-unique
     * index, so JAuth and Mongo can never disagree). The email VALUE is mirrored
     * into the Mongo profile by the NoeFix caller; the verified flag stays
     * authoritative here in JAuth.
     *
     * <p>Edge cases:
     * <ul>
     *   <li>Same email re-submitted + already verified → reject (nothing to do).</li>
     *   <li>Same email re-submitted + not verified → just resend the link (rate-limited), no reset.</li>
     *   <li>Email belongs to another active user → 409 (no silent reclaim).</li>
     *   <li>On a genuine change, old verification tokens are invalidated so an old link can't verify the new address.</li>
     * </ul>
     */
    @Transactional
    public UserProfileResponse setEmail(Long userId, String rawEmail) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (rawEmail == null || rawEmail.isBlank()) {
            throw new VerificationException("Email is required");
        }
        final String norm = rawEmail.trim().toLowerCase();
        final String current = user.getEmail();

        // Same email re-submitted.
        if (current != null && current.equalsIgnoreCase(norm)) {
            if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
                throw new VerificationException("This email is already set and verified.");
            }
            // Unverified — resend the link (rate-limited to prevent spam); no DB change.
            emailVerificationService.sendVerificationEmail(userId);
            logger.info("Email unchanged but unverified — resent verification link: userId={}", userId);
            return toProfileResponse(user);
        }

        // New or changed email — strict uniqueness across all ACTIVE users.
        userRepository.findByEmailAndIsActiveTrue(norm).ifPresent(other -> {
            if (!other.getId().equals(userId)) {
                throw new DuplicateResourceException("User", "email", rawEmail);
            }
        });

        user.setEmail(norm);
        user.setIsEmailVerified(false);
        final User saved = userRepository.save(user);
        logger.info("Email {} for user: userId={} — verification reset, sending link",
                current == null ? "added" : "changed", userId);

        // Fresh link; skips resend-rate-limit and invalidates old tokens internally.
        emailVerificationService.sendVerificationOnEmailSet(userId);

        return toProfileResponse(saved);
    }

    /** Build the standard profile response from a User entity. */
    private UserProfileResponse toProfileResponse(User u) {
        return new UserProfileResponse(
                u.getId(),
                u.getEmail(),
                u.getPhoneNumber(),
                u.getFullName(),
                u.getRole(),
                u.getIsActive(),
                u.getIsEmailVerified(),
                u.getIsPhoneVerified(),
                u.getCreatedAt(),
                u.getUpdatedAt(),
                u.getLastLoginAt(),
                u.getPasswordHash() != null && !u.getPasswordHash().isBlank()
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

    /**
     * List users/providers with pagination, search, and filtering.
     * Admin-only operation.
     */
    public AdminUserListResponse listUsers(String roleFilter, String statusFilter, String search, int page, int size) {
        // Clamp page size
        if (size < 1) size = 20;
        if (size > 100) size = 100;
        if (page < 0) page = 0;

        // Determine roles to query
        List<Role> roles;
        if ("USER".equalsIgnoreCase(roleFilter)) {
            roles = Arrays.asList(Role.USER);
        } else if ("SERVICE_PROVIDER".equalsIgnoreCase(roleFilter)) {
            roles = Arrays.asList(Role.SERVICE_PROVIDER);
        } else {
            // Default: both USER and SERVICE_PROVIDER (exclude admin roles)
            roles = Arrays.asList(Role.USER, Role.SERVICE_PROVIDER);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> userPage;
        boolean hasSearch = search != null && !search.trim().isEmpty();
        Boolean isActive = null;
        if ("active".equalsIgnoreCase(statusFilter)) isActive = true;
        else if ("disabled".equalsIgnoreCase(statusFilter)) isActive = false;

        if (hasSearch && isActive != null) {
            userPage = userRepository.searchByRolesAndQueryAndStatus(roles, search.trim(), isActive, pageable);
        } else if (hasSearch) {
            userPage = userRepository.searchByRolesAndQuery(roles, search.trim(), pageable);
        } else if (isActive != null) {
            userPage = userRepository.findByRoleInAndIsActive(roles, isActive, pageable);
        } else {
            userPage = userRepository.findByRoleIn(roles, pageable);
        }

        List<UserProfileResponse> userResponses = userPage.getContent().stream()
                .map(user -> new UserProfileResponse(
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
                ))
                .collect(Collectors.toList());

        return new AdminUserListResponse(
                userResponses,
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages()
        );
    }

    /**
     * Get a single user by ID.
     * Admin-only operation.
     */
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    @Transactional
    public UserProfileResponse createUserByAdmin(AdminCreateUserRequest request) {
        logger.debug("Admin creating user with email: {}", request.getEmail());

        Role requestedRole = request.getRole();
        if (requestedRole != Role.ADMIN && requestedRole != Role.IT_ADMIN && requestedRole != Role.SUPPORT) {
            throw new InvalidRoleException(requestedRole.name(),
                    "Admin user creation only allows ADMIN, IT_ADMIN, or SUPPORT roles. Use public registration for USER/SERVICE_PROVIDER.");
        }

        String adminEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailAndIsEmailVerifiedTrueAndIsActiveTrue(adminEmail)) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        // Reclaim unverified email if held by another account
        userRepository.findByEmailAndIsActiveTrue(adminEmail).ifPresent(oldUser -> {
            if (!Boolean.TRUE.equals(oldUser.getIsEmailVerified())) {
                oldUser.setEmail("unclaimed_" + oldUser.getId() + "@placeholder.local");
                oldUser.setIsActive(false);
                if (oldUser.getPhoneNumber() != null) oldUser.setPhoneNumber("del_" + oldUser.getId());
                userRepository.save(oldUser);
            }
        });

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
    public String requestDeleteAccountOtp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

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

        logger.info("Delete account OTP sent for user: userId={} to phone: {}",
                userId, maskPhoneNumber(phoneNumber));

        return maskPhoneNumber(phoneNumber);
    }

    /**
     * Delete user account with OTP verification.
     * OTP is verified from database.
     */
    @Transactional
    public MessageResponse deleteAccountWithOtp(Long userId, DeleteAccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

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
            logger.warn("Invalid OTP attempt for account deletion. User: userId={}", userId);
            throw new InvalidPasswordException("Invalid or expired OTP. Please request a new code.");
        }

        // OTP verified — mark as used
        otpEntry.markAsUsed();
        deleteAccountOtpRepository.save(otpEntry);

        // Perform soft delete
        String reason = request.getReason() != null ? request.getReason() : "User requested deletion";
        performSoftDelete(user, reason);

        logger.info("Account deleted for user: userId={} - OTP verified", user.getId());

        return new MessageResponse("Account deleted successfully. We're sorry to see you go.");
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "******";
        }
        String lastFour = phoneNumber.substring(phoneNumber.length() - 4);
        return "******" + lastFour;
    }

    public boolean isUserAuthorizedForDeletion(Long currentUserId, Long targetUserId) {
        User currentUser = userRepository.findById(currentUserId).orElse(null);
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

    /**
     * Admin-only hard delete: permanently removes the user row and all
     * child records (refresh tokens, trusted devices, sessions, delete-OTPs,
     * login lockouts). Irreversible — intended for cleaning up tombstoned
     * accounts where compliance retention is not required.
     */
    @Transactional
    public MessageResponse hardDeleteUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        int tokens = refreshTokenRepository.deleteAllByUserId(userId);
        int devices = trustedDeviceRepository.deleteAllByUserId(userId);
        int sessions = userSessionRepository.deleteAllByUserId(userId);
        int otps = deleteAccountOtpRepository.deleteAllByUserId(userId);
        int lockouts = loginLockoutRepository.deleteAllByUserId(userId);

        userRepository.delete(user);

        logger.warn("HARD DELETE — user ID {} (email: {}). Removed: {} tokens, {} devices, {} sessions, {} delete-OTPs, {} lockouts.",
            userId, user.getEmail(), tokens, devices, sessions, otps, lockouts);

        return new MessageResponse("User permanently deleted");
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
