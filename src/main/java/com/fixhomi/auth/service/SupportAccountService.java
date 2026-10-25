package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.UpdateUserStatusRequest;
import com.fixhomi.auth.dto.UserProfileResponse;
import com.fixhomi.auth.entity.SupportPermission;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.VerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONE thing Support does inside jauth: switch a customer/provider account on or off.
 * That switch (users.is_active) is identity data, so it must happen here, and only with the
 * ACCOUNT_STATUS_UPDATE permission. It REUSES the existing UserService.updateUserStatus.
 */
@Service
public class SupportAccountService {

    private static final Logger log = LoggerFactory.getLogger(SupportAccountService.class);

    private final UserService userService;
    private final SupportAccessService accessService;

    public SupportAccountService(UserService userService, SupportAccessService accessService) {
        this.userService = userService;
        this.accessService = accessService;
    }

    @Transactional
    public UserProfileResponse updateCustomerStatus(Long userId, UpdateUserStatusRequest request) {
        User staff = accessService.requirePermission(SupportPermission.ACCOUNT_STATUS_UPDATE);
        User target = accessService.requireCustomer(userId);       // never an admin or another support user

        if (Boolean.TRUE.equals(request.getIsActive()) && isDeletedOrReclaimed(target)) {
            throw new VerificationException("This account was deleted and cannot be re-enabled by Support.");
        }

        UserProfileResponse result = userService.updateUserStatus(userId, request);
        log.info("Support/admin user {} set account {} active={}", staff.getId(), userId, request.getIsActive());
        return result;
    }

    /** Deleted / reclaimed accounts carry placeholder identifiers. Reviving them would create a broken account. */
    private boolean isDeletedOrReclaimed(User user) {
        String email = user.getEmail();
        String phone = user.getPhoneNumber();
        return (email != null && (email.endsWith("@del.local") || email.endsWith("@placeholder.local")))
                || (phone != null && phone.startsWith("del_"));
    }
}
