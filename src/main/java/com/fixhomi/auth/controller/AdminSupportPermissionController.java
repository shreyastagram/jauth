package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.SupportDto;
import com.fixhomi.auth.service.SupportPermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only. It sits under /api/admin/**, which SecurityConfig already limits to ADMIN / IT_ADMIN,
 * so a SUPPORT user can never reach it ("Support cannot give permissions to another person").
 */
@RestController
@RequestMapping("/api/admin/support-staff/{userId}/permissions")
@PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")
public class AdminSupportPermissionController {

    private final SupportPermissionService permissionService;

    public AdminSupportPermissionController(SupportPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    public ResponseEntity<SupportDto.PermissionsResponse> list(@PathVariable Long userId) {
        return ResponseEntity.ok(new SupportDto.PermissionsResponse(userId, permissionService.list(userId)));
    }

    @PutMapping("/{permission}")
    public ResponseEntity<SupportDto.PermissionsResponse> grant(@PathVariable Long userId,
                                                                @PathVariable String permission) {
        return ResponseEntity.ok(new SupportDto.PermissionsResponse(userId, permissionService.grant(userId, permission)));
    }

    @DeleteMapping("/{permission}")
    public ResponseEntity<SupportDto.PermissionsResponse> revoke(@PathVariable Long userId,
                                                                 @PathVariable String permission) {
        return ResponseEntity.ok(new SupportDto.PermissionsResponse(userId, permissionService.revoke(userId, permission)));
    }
}
