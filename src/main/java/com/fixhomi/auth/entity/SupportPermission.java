package com.fixhomi.auth.entity;

/**
 * Optional extra powers that an admin can give to ONE support agent.
 * A support agent never has these by default ("only if specifically permitted").
 * To add another optional power later, add one more value here.
 */
public enum SupportPermission {
    /** Allows the agent to block (deactivate) or unblock a customer / service provider account. */
    BLOCK_ACCOUNTS
}