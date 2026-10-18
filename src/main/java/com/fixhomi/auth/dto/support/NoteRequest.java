package com.fixhomi.auth.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for adding or editing a note. */
public record NoteRequest(@NotBlank @Size(max = 1000) String content) {
}