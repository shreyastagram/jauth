package com.fixhomi.auth.service;

import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.SupportPermission;
import com.fixhomi.auth.entity.SupportPermissionGrant;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.ResourceNotFoundException;
import com.fixhomi.auth.repository.SupportPermissionGrantRepository;
import com.fixhomi.auth.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;

/**
 * The single place that answers "who is calling, and are they allowed?".
 * It reads the user from the DATABASE (not from the token), so a demoted / disabled user
 * or a revoked permission is blocked on the very next request. (A token's role can be up to 24 hours old.)
 */
@Service
public class SupportAccessService {

    private final UserRepository userRepository;
    private final SupportPermissionGrantRepository grantRepository;

    public SupportAccessService(UserRepository userRepository,
                                SupportPermissionGrantRepository grantRepository) {
        this.userRepository = userRepository;
        this.grantRepository = grantRepository;
    }

    public static boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.IT_ADMIN;
    }

    /** The logged-in staff member (SUPPORT, ADMIN or IT_ADMIN), loaded fresh from the DB. */
    public User currentStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        Long userId;
        try {
            userId = Long.valueOf(auth.getName());      // the JWT filter stores the user id here
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("Invalid principal");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("Account not found"));

        boolean isStaff = user.getRole() == Role.SUPPORT || isAdmin(user);
        if (!isStaff || !Boolean.TRUE.equals(user.getIsActive())) {
            throw new AccessDeniedException("Staff access required");
        }
        return user;
    }

    /** Only ADMIN / IT_ADMIN (checked against the DB role). */
    public User requireAdmin() {
        User user = currentStaff();
        if (!isAdmin(user)) {
            throw new AccessDeniedException("Admin access required");
        }
        return user;
    }

    public boolean hasPermission(User staff, SupportPermission permission) {
        if (isAdmin(staff)) {
            return true;                                // admins can do everything
        }
        return grantRepository.existsByUserIdAndPermission(staff.getId(), permission.name());
    }

    /** Returns the staff member, or throws (HTTP 403) if the permission was not granted. */
    public User requirePermission(SupportPermission permission) {
        User staff = currentStaff();
        if (!hasPermission(staff, permission)) {
            throw new AccessDeniedException("Missing permission: " + permission.name());
        }
        return staff;
    }

    /** All permissions this staff member has (admins have every one). */
    public List<String> permissionsOf(User staff) {
        if (isAdmin(staff)) {
            return Arrays.stream(SupportPermission.values()).map(Enum::name).sorted().toList();
        }
        return grantRepository.findByUserId(staff.getId()).stream()
                .map(SupportPermissionGrant::getPermission).sorted().toList();
    }

    /** A customer or service provider. Anybody else (staff, missing id) looks like "not found". */
    public User requireCustomer(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (user.getRole() != Role.USER && user.getRole() != Role.SERVICE_PROVIDER) {
            throw new ResourceNotFoundException("User", "id", userId);
        }
        return user;
    }
}
