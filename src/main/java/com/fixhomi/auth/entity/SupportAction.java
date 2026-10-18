package com.fixhomi.auth.entity;

/** Every kind of support activity that is written to the audit log. */
public enum SupportAction {
    TICKET_CREATED,
    TICKET_UPDATED,
    TICKET_ASSIGNED,
    TICKET_RESOLVED,
    CUSTOMER_VIEWED,
    ACCOUNT_UNLOCKED,
    FORCE_LOGOUT,
    VERIFICATION_EMAIL_RESENT,
    ACCOUNT_BLOCKED,
    ACCOUNT_UNBLOCKED,
    NOTE_ADDED,
    NOTE_EDITED,
    REPORT_EXPORTED,
    PERMISSION_GRANTED,
    PERMISSION_REVOKED
}