package com.fixhomi.auth.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for blocking an account. The reason is saved in the audit log. */
public record BlockRequest(@NotBlank @Size(max = 300) String reason) {
}