package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.UserNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserNoteRepository extends JpaRepository<UserNote, Long> {

    List<UserNote> findByTargetUserIdOrderByCreatedAtDesc(Long targetUserId);
}