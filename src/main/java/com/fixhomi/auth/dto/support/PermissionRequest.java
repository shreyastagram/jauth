package com.fixhomi.auth.dto.support;

import com.fixhomi.auth.entity.SupportPermission;
import jakarta.validation.constraints.NotNull;

/** Body used by an admin to give or take back an optional permission. */
public record PermissionRequest(@NotNull SupportPermission permission) {
}