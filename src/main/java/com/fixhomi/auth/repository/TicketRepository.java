package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.Ticket;
import com.fixhomi.auth.entity.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    Page<Ticket> findByCreatedByUserId(Long userId, Pageable pageable);

    /** Used by the daily report: how many tickets were raised in a time window. */
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);

    /** Used by the dashboard. */
    long countByStatus(TicketStatus status);
}