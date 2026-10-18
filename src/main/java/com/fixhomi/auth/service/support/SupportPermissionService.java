package com.fixhomi.auth.service.support;

import com.fixhomi.auth.entity.*;
import com.fixhomi.auth.exception.ResourceNotFoundException;
import com.fixhomi.auth.exception.SupportBadRequestException;
import com.fixhomi.auth.repository.SupportPermissionGrantRepository;
import com.fixhomi.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin gives / takes back optional powers; other services ask here before doing a restricted action. */
@Service
public class SupportPermissionService {

    @Autowired
    private SupportPermissionGrantRepository grantRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupportAuditService auditService;

    /**
     * Throws AccessDeniedException (-> 403) unless the actor may use the permission.
     * ADMIN / IT_ADMIN always may. A SUPPORT agent needs a grant. The role is read from the database
     * (not the JWT) so a role change takes effect immediately.
     */
    @Transactional(readOnly = true)
    public void require(Long actorUserId, SupportPermission permission) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));
        if (actor.getRole() == Role.ADMIN || actor.getRole() == Role.IT_ADMIN) {
            return;
        }
        if (actor.getRole() != Role.SUPPORT
                || !grantRepository.existsByUserIdAndPermission(actorUserId, permission)) {
            throw new AccessDeniedException("You do not have the " + permission + " permission.");
        }
    }

    @Transactional
    public void grant(Long adminId, Long supportUserId, SupportPermission permission) {
        User agent = findSupportAgent(supportUserId);
        if (!grantRepository.existsByUserIdAndPermission(agent.getId(), permission)) {
            grantRepository.save(new SupportPermissionGrant(agent.getId(), permission, adminId));
        }
        auditService.log(adminId, SupportAction.PERMISSION_GRANTED, "USER", agent.getId(), permission.name());
    }

    @Transactional
    public void revoke(Long adminId, Long supportUserId, SupportPermission permission) {
        User agent = findSupportAgent(supportUserId);
        grantRepository.deleteByUserIdAndPermission(agent.getId(), permission);
        auditService.log(adminId, SupportAction.PERMISSION_REVOKED, "USER", agent.getId(), permission.name());
    }

    @Transactional(readOnly = true)
    public List<SupportPermissionGrant> list(Long supportUserId) {
        return grantRepository.findByUserId(supportUserId);
    }

    private User findSupportAgent(Long id) {
        User u = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        if (u.getRole() != Role.SUPPORT) {
            throw new SupportBadRequestException("Permissions can only be given to a SUPPORT user.");
        }
        return u;
    }
}