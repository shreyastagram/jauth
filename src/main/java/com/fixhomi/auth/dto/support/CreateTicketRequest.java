package com.fixhomi.auth.dto.support;

import com.fixhomi.auth.entity.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for creating a ticket. */
public record CreateTicketRequest(
        @NotNull Long customerUserId,
        @NotBlank @Size(max = 150) String subject,
        @NotBlank @Size(max = 2000) String description,
        TicketPriority priority) {
}