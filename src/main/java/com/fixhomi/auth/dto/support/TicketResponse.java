package com.fixhomi.auth.dto.support;

import com.fixhomi.auth.entity.Ticket;
import com.fixhomi.auth.entity.TicketPriority;
import com.fixhomi.auth.entity.TicketStatus;

import java.time.LocalDateTime;

/** What the API returns for a ticket. */
public record TicketResponse(
        Long id, String subject, String description, TicketStatus status, TicketPriority priority,
        Long createdByUserId, Long assignedToUserId, String resolutionNote,
        LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime resolvedAt) {

    public static TicketResponse from(Ticket t) {
        return new TicketResponse(t.getId(), t.getSubject(), t.getDescription(), t.getStatus(), t.getPriority(),
                t.getCreatedByUserId(), t.getAssignedToUserId(), t.getResolutionNote(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getResolvedAt());
    }
}