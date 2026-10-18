package com.fixhomi.auth.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for resolving a ticket. */
public record ResolveTicketRequest(@NotBlank @Size(max = 2000) String resolutionNote) {
}