package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.AdminCreateUserRequest;
import com.fixhomi.auth.dto.AdminUserListResponse;
import com.fixhomi.auth.dto.MessageResponse;
import com.fixhomi.auth.dto.UpdateUserStatusRequest;
import com.fixhomi.auth.dto.UserProfileResponse;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for admin user management operations.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Autowired
    private UserService userService;

    /**
     * Enable or disable a user account.
     * PATCH /api/admin/users/{userId}/status
     * 
     * Access: ADMIN or IT_ADMIN only
     *
     * @param userId user ID to update
     * @param request status update request
     * @return updated user profile
     */
    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")
    public ResponseEntity<UserProfileResponse> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        
        UserProfileResponse response = userService.updateUserStatus(userId, request);
        return ResponseEntity.ok(response);
    }

    
    /**
     * Create a new user with elevated roles (ADMIN, IT_ADMIN, SUPPORT).
     * POST /api/admin/users
     * 
     * Access: ADMIN or IT_ADMIN only
     * 
     * This endpoint allows admins to create users with roles that are not
     * allowed through public registration (ADMIN, IT_ADMIN, SUPPORT).
     *
     * @param request admin create user request
     * @return created user profile with generated password (if applicable)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")
    public ResponseEntity<UserProfileResponse> createUser(
            @Valid @RequestBody AdminCreateUserRequest request) {
        
        UserProfileResponse response = userService.createUserByAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all users/providers with pagination, search, and filtering.
     * GET /api/admin/users/list
     *
     * Query params:
     *   role: USER | SERVICE_PROVIDER (default: both)
     *   status: active | disabled (default: all)
     *   search: search by name, email, or phone
     *   page: page number (0-based, default: 0)
     *   size: page size (default: 20, max: 100)
     *
     * Access: ADMIN or IT_ADMIN only
     */
    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")
    public ResponseEntity<AdminUserListResponse> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AdminUserListResponse response = userService.listUsers(role, status, search, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a single user's full auth profile by ID.
     * GET /api/admin/users/{userId}
     *
     * Access: ADMIN or IT_ADMIN only
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")
    public ResponseEntity<UserProfileResponse> getUserById(@PathVariable Long userId) {
        User user = userService.getUserById(userId);
        UserProfileResponse response = new UserProfileResponse(
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
        return ResponseEntity.ok(response);
    }

    /**
     * Permanently delete a user and all child records (refresh tokens, trusted
     * devices, sessions, delete-OTPs, login lockouts). Irreversible.
     *
     * DELETE /api/admin/users/{userId}
     *
     * Access: ADMIN or IT_ADMIN only. The upstream admin panel additionally
     * gates this with a "super admin" check before calling through.
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")
    public ResponseEntity<MessageResponse> hardDeleteUser(@PathVariable Long userId) {
        MessageResponse response = userService.hardDeleteUserById(userId);
        return ResponseEntity.ok(response);
    }
}
