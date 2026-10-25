package com.fixhomi.auth.dto;

import java.util.List;

/** Response shapes of the Support-permission API. A "record" is a tiny read-only class. */
public final class SupportDto {

    private SupportDto() {
    }

    /** "Who am I and what extra powers do I have?" Used by the Node backend and the admin panel. */
    public record MeResponse(Long userId, String role, List<String> permissions) {
    }

    /** The permissions currently held by one support user (admin view). */
    public record PermissionsResponse(Long userId, List<String> permissions) {
    }
}
