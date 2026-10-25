package com.fixhomi.auth.service;

import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.SupportPermission;
import com.fixhomi.auth.entity.SupportPermissionGrant;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.InvalidRoleException;
import com.fixhomi.auth.exception.ResourceNotFoundException;
import com.fixhomi.auth.repository.SupportPermissionGrantRepository;
import com.fixhomi.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Admin-only: give / take away / list the extra permissions of SUPPORT users. */
@Service
public class SupportPermissionService {

    private static final Logger log = LoggerFactory.getLogger(SupportPermissionService.class);

    private final UserRepository userRepository;
    private final SupportPermissionGrantRepository grantRepository;
    private final SupportAccessService accessService;

    public SupportPermissionService(UserRepository userRepository,
                                    SupportPermissionGrantRepository grantRepository,
                                    SupportAccessService accessService) {
        this.userRepository = userRepository;
        this.grantRepository = grantRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public List<String> list(Long targetUserId) {
        accessService.requireAdmin();
        requireSupportUser(targetUserId);
        return currentPermissions(targetUserId);
    }

    @Transactional
    public List<String> grant(Long targetUserId, String permissionName) {
        User admin = accessService.requireAdmin();
        SupportPermission permission = SupportPermission.from(permissionName);
        User target = requireSupportUser(targetUserId);

        // Granting twice is harmless: we simply do nothing the second time.
        if (!grantRepository.existsByUserIdAndPermission(target.getId(), permission.name())) {
            grantRepository.save(new SupportPermissionGrant(target.getId(), permission.name(), admin.getId()));
            log.info("Support permission {} GRANTED to user {} by admin {}",
                    permission.name(), target.getId(), admin.getId());
        }
        return currentPermissions(target.getId());
    }

    @Transactional
    public List<String> revoke(Long targetUserId, String permissionName) {
        User admin = accessService.requireAdmin();
        SupportPermission permission = SupportPermission.from(permissionName);
        User target = requireSupportUser(targetUserId);

        grantRepository.findByUserIdAndPermission(target.getId(), permission.name()).ifPresent(grant -> {
            grantRepository.delete(grant);
            log.info("Support permission {} REVOKED from user {} by admin {}",
                    permission.name(), target.getId(), admin.getId());
        });
        return currentPermissions(target.getId());
    }

    private List<String> currentPermissions(Long userId) {
        return grantRepository.findByUserId(userId).stream()
                .map(SupportPermissionGrant::getPermission).sorted().toList();
    }

    /** Permissions only make sense for SUPPORT accounts. */
    private User requireSupportUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (user.getRole() != Role.SUPPORT) {
            throw new InvalidRoleException(user.getRole().name(),
                    "permissions can only be managed for SUPPORT users");
        }
        return user;
    }
}
