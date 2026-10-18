package com.fixhomi.auth.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * "Who did what, to whom, and when" for support work.
 * The app only ever inserts rows here. Nothing edits or deletes them. Table: support_audit_logs
 */
@Entity
@Table(name = "support_audit_logs", indexes = {
        @Index(name = "idx_support_audit_actor", columnList = "actor_user_id")
})
@EntityListeners(AuditingEntityListener.class)
public class SupportAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The staff member who did the action. */
    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    /** Column is called action_type because "action" can be a reserved word in some databases. */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private SupportAction action;

    /** What the action was done to: "USER", "TICKET", "NOTE", "REPORT" ... */
    @Column(length = 30)
    private String targetType;

    @Column
    private Long targetId;

    @Column(length = 500)
    private String details;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public SupportAuditLog() {
    }

    public SupportAuditLog(Long actorUserId, SupportAction action, String targetType, Long targetId, String details) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details;
    }

    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public SupportAction getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}