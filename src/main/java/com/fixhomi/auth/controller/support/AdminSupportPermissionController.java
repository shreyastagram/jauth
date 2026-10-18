package com.fixhomi.auth.controller.support;

import com.fixhomi.auth.dto.MessageResponse;
import com.fixhomi.auth.dto.support.PermissionRequest;
import com.fixhomi.auth.entity.SupportPermission;
import com.fixhomi.auth.service.GoogleAuthService;
import com.fixhomi.auth.service.support.SupportPermissionService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;



/** Admin-only: give or take back optional permissions of a support agent. URL is under /api/admin/** so it is admin-only. */
@RestController
@RequestMapping("/api/admin/support-permissions")
public class AdminSupportPermissionController {

    @Autowired
    private SupportPermissionService permissionService;

    @GetMapping("/{supportUserId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")
    public ResponseEntity<List<SupportPermission>> list(@PathVariable Long supportUserId) {
       
        return ResponseEntity.ok(permissionService.list(supportUserId).stream()
                .map(g -> g.getPermission()).toList());
    }

    @PostMapping("/{supportUserId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")
    public ResponseEntity<MessageResponse> grant(Authentication auth, @PathVariable Long supportUserId,
                                                 @Valid @RequestBody PermissionRequest req) {
                                            
        permissionService.grant(Long.valueOf(auth.getName()), supportUserId, req.permission());
       
        return ResponseEntity.ok(new MessageResponse("Permission granted"));
    }

    @DeleteMapping("/{supportUserId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")
    public ResponseEntity<MessageResponse> revoke(Authentication auth, @PathVariable Long supportUserId,
                                                  @Valid @RequestBody PermissionRequest req) {
        permissionService.revoke(Long.valueOf(auth.getName()), supportUserId, req.permission());
        return ResponseEntity.ok(new MessageResponse("Permission revoked"));
    }
}