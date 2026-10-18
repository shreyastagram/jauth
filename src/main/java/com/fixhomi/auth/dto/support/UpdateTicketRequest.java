package com.fixhomi.auth.dto.support;

import com.fixhomi.auth.entity.TicketPriority;
import com.fixhomi.auth.entity.TicketStatus;
import jakarta.validation.constraints.Size;

/** Body for updating a ticket. Send only the fields you want to change. */
public record UpdateTicketRequest(
        TicketStatus status,
        TicketPriority priority,
        @Size(max = 2000) String description) {
}