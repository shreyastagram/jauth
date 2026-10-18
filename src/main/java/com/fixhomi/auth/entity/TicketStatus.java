package com.fixhomi.auth.entity;

/** The life of a support ticket: OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED. */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}