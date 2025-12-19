package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.AdminCreateUserRequest;
import com.fixhomi.auth.dto.UpdateUserStatusRequest;
import com.fixhomi.auth.dto.UserProfileResponse;
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
}
