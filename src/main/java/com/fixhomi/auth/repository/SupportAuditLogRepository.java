package com.fixhomi.auth.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fixhomi.auth.entity.SupportAuditLog;

@Repository
public interface SupportAuditLogRepository extends JpaRepository<SupportAuditLog, Long> {

    /** A support agent only ever reads rows where they are the actor ("own activity"). */
    Page<SupportAuditLog> findByActorUserId(Long actorUserId, Pageable pageable);

    /** For the daily report: this agent's own actions on one day. */
    List<SupportAuditLog> findByActorUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            Long actorUserId, LocalDateTime start, LocalDateTime end);
}