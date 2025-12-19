package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.ChangePasswordRequest;
import com.fixhomi.auth.dto.MessageResponse;
import com.fixhomi.auth.dto.UserProfileResponse;
import com.fixhomi.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user account operations.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * Get authenticated user's profile.
     * GET /api/users/me
     *
     * @return user profile
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile() {
        String email = getCurrentUserEmail();
        UserProfileResponse profile = userService.getUserProfile(email);
        return ResponseEntity.ok(profile);
    }

    /**
     * Change password for authenticated user.
     * POST /api/users/change-password
     *
     * @param request change password request
     * @return success message
     */
    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String email = getCurrentUserEmail();
        MessageResponse response = userService.changePassword(email, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get current authenticated user's email from SecurityContext.
     */
    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName(); // email is set as principal in JwtAuthenticationFilter
    }
}
