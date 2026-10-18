package com.fixhomi.auth.service.support;

import com.fixhomi.auth.dto.support.DashboardResponse;
import com.fixhomi.auth.dto.support.SupportUserResponse;
import com.fixhomi.auth.entity.*;
import com.fixhomi.auth.exception.ResourceNotFoundException;
import com.fixhomi.auth.exception.SupportBadRequestException;
import com.fixhomi.auth.repository.*;
import com.fixhomi.auth.service.EmailVerificationService;
import com.fixhomi.auth.service.RefreshTokenService;
import com.fixhomi.auth.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Support actions on customers and service providers (and only on them). */
@Service
public class SupportCustomerService {

    private static final List<Role> CUSTOMER_ROLES = List.of(Role.USER, Role.SERVICE_PROVIDER);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupportReportUserRepository reportUserRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private LoginLockoutRepository lockoutRepository;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private EmailVerificationService emailVerificationService;
    @Autowired
    private SupportPermissionService permissionService;
    @Autowired
    private SupportAuditService auditService;

    /** Guard used by every support action: the target must be a customer or a provider. */
    public static void requireCustomerOrProvider(User u) {
        if (u.getRole() != Role.USER && u.getRole() != Role.SERVICE_PROVIDER) {
            throw new ResourceNotFoundException("Customer or provider", "id", u.getId());
        }
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        return new DashboardResponse(
                reportUserRepository.countByRole(Role.USER),
                reportUserRepository.countByRole(Role.SERVICE_PROVIDER),
                ticketRepository.countByStatus(TicketStatus.OPEN),
                ticketRepository.countByStatus(TicketStatus.IN_PROGRESS),
                ticketRepository.countByStatus(TicketStatus.RESOLVED));
    }

    @Transactional(readOnly = true)
    public Page<SupportUserResponse> search(String query, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pr = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result = (query == null || query.isBlank())
                ? userRepository.findByRoleIn(CUSTOMER_ROLES, pr)
                : userRepository.searchByRolesAndQuery(CUSTOMER_ROLES, query.trim(), pr);
        return result.map(this::toResponse);
    }

    @Transactional
    public SupportUserResponse view(Long agentId, Long userId) {
        User u = find(userId);
        auditService.log(agentId, SupportAction.CUSTOMER_VIEWED, "USER", userId, null);
        return toResponse(u);
    }

    /** Clears a "too many wrong passwords" lock. */
    @Transactional
    public int unlock(Long agentId, Long userId) {
        find(userId);
        List<LoginLockout> locks = lockoutRepository.findByUserId(userId);
        for (LoginLockout l : locks) {
            l.resetAttempts();
        }
        lockoutRepository.saveAll(locks);
        auditService.log(agentId, SupportAction.ACCOUNT_UNLOCKED, "USER", userId, locks.size() + " lock(s) cleared");
        return locks.size();
    }

    /** Signs the person out of every device. */
    @Transactional
    public int forceLogout(Long agentId, Long userId) {
        User u = find(userId);
        int tokens = refreshTokenService.revokeAllUserTokens(userId);
        int sessions = sessionService.revokeAllSessions(u);
        auditService.log(agentId, SupportAction.FORCE_LOGOUT, "USER", userId,
                tokens + " token(s), " + sessions + " session(s) revoked");
        return tokens + sessions;
    }

    @Transactional
    public String resendVerificationEmail(Long agentId, Long userId) {
        find(userId);
        String masked = emailVerificationService.sendVerificationEmail(userId);
        auditService.log(agentId, SupportAction.VERIFICATION_EMAIL_RESENT, "USER", userId, null);
        return masked;
    }

    /** Needs the BLOCK_ACCOUNTS permission (admins always have it). */
    @Transactional
    public void block(Long agentId, Long userId, String reason) {
        permissionService.require(agentId, SupportPermission.BLOCK_ACCOUNTS);
        User u = find(userId);
        if (!Boolean.TRUE.equals(u.getIsActive())) {
            throw new SupportBadRequestException("This account is already blocked.");
        }
        u.setIsActive(false);
        userRepository.save(u);
        refreshTokenService.revokeAllUserTokens(userId);
        sessionService.revokeAllSessions(u);
        auditService.log(agentId, SupportAction.ACCOUNT_BLOCKED, "USER", userId, reason);
    }

    @Transactional
    public void unblock(Long agentId, Long userId) {
        permissionService.require(agentId, SupportPermission.BLOCK_ACCOUNTS);
        User u = find(userId);
        if (Boolean.TRUE.equals(u.getIsActive())) {
            throw new SupportBadRequestException("This account is not blocked.");
        }
        u.setIsActive(true);
        userRepository.save(u);
        auditService.log(agentId, SupportAction.ACCOUNT_UNBLOCKED, "USER", userId, null);
    }

    private User find(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        requireCustomerOrProvider(u);
        return u;
    }

    private SupportUserResponse toResponse(User u) {
        return SupportUserResponse.from(u, DataMasking.maskEmail(u.getEmail()), DataMasking.maskPhone(u.getPhoneNumber()));
    }
}