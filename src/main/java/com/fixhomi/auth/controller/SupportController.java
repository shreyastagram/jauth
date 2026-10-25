package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.SupportDto;
import com.fixhomi.auth.dto.UpdateUserStatusRequest;
import com.fixhomi.auth.dto.UserProfileResponse;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.service.SupportAccessService;
import com.fixhomi.auth.service.SupportAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What a SUPPORT user (or admin) can ask jauth: "what may I do?" and "switch this account on/off". */
@RestController
@RequestMapping("/api/support")
@PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN', 'IT_ADMIN')")
public class SupportController {

    private final SupportAccessService accessService;
    private final SupportAccountService accountService;

    public SupportController(SupportAccessService accessService, SupportAccountService accountService) {
        this.accessService = accessService;
        this.accountService = accountService;
    }

    /**
     * The Node backend calls this (forwarding the user's own token) before allowing a permission-gated action.
     * It also doubles as a LIVE check: a demoted or disabled user gets 403 here immediately.
     */
    @GetMapping("/me")
    public ResponseEntity<SupportDto.MeResponse> me() {
        User staff = accessService.currentStaff();
        return ResponseEntity.ok(new SupportDto.MeResponse(
                staff.getId(), staff.getRole().name(), accessService.permissionsOf(staff)));
    }

    /** Needs the ACCOUNT_STATUS_UPDATE permission. Body: {"isActive": true|false} */
    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<UserProfileResponse> updateStatus(@PathVariable Long userId,
                                                            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(accountService.updateCustomerStatus(userId, request));
    }
}
