package com.fixhomi.auth.controller.support;

import com.fixhomi.auth.dto.support.AuditLogResponse;
import com.fixhomi.auth.dto.support.DashboardResponse;
import com.fixhomi.auth.service.support.SupportAuditService;
import com.fixhomi.auth.service.support.SupportCustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Dashboard (view only) and the agent's own audit activity (view only). */
@RestController
@RequestMapping("/api/support")
public class SupportDashboardController {

    @Autowired
    private SupportCustomerService customerService;
    @Autowired
    private SupportAuditService auditService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard() {
        return ResponseEntity.ok(customerService.dashboard());
    }

    @GetMapping("/audit-logs/me")
    public ResponseEntity<Page<AuditLogResponse>> myActivity(Authentication auth,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        Long agentId = Long.valueOf(auth.getName());
        return ResponseEntity.ok(auditService.myActivity(agentId, page, size).map(AuditLogResponse::from));
    }
}