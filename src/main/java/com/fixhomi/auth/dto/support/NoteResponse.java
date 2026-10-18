package com.fixhomi.auth.dto.support;

import com.fixhomi.auth.entity.UserNote;

import java.time.LocalDateTime;

public record NoteResponse(Long id, Long targetUserId, Long authorUserId, String content,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static NoteResponse from(UserNote n) {
        return new NoteResponse(n.getId(), n.getTargetUserId(), n.getAuthorUserId(), n.getContent(),
                n.getCreatedAt(), n.getUpdatedAt());
    }
}