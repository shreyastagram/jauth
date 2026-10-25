package com.fixhomi.auth.entity;

import com.fixhomi.auth.exception.VerificationException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Extra powers an admin can grant to ONE support user.
 * jauth only STORES and REPORTS these. Everything else in the PDF (tickets, notes, reports,
 * dashboard, ...) is business logic owned by the Node backend.
 */
public enum SupportPermission {

    /**
     * May disable / re-enable customer and provider accounts (PDF: "only if specifically permitted").
     * The account switch (users.is_active) lives in jauth, so jauth itself enforces this one (Step 8).
     */
    ACCOUNT_STATUS_UPDATE,

    /**
     * May change the status of service requests (PDF: "permitted status updates").
     * NOTE: service requests live in the Node.js backend, so jauth only STORES this permission.
     * The Node backend must check it by calling GET /api/support/me (see section 10).
     */
    SERVICE_REQUEST_STATUS_UPDATE;

    public static SupportPermission from(String value) {
        if (value == null || value.isBlank()) {
            throw new VerificationException("Permission name is required. Allowed: " + Arrays.toString(values()));
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new VerificationException("Unknown permission '" + value + "'. Allowed: " + Arrays.toString(values()));
        }
    }
}
