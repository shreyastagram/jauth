package com.fixhomi.auth.service.support;

import com.fixhomi.auth.dto.support.NoteRequest;
import com.fixhomi.auth.dto.support.NoteResponse;
import com.fixhomi.auth.entity.SupportAction;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.entity.UserNote;
import com.fixhomi.auth.exception.ResourceNotFoundException;
import com.fixhomi.auth.repository.UserNoteRepository;
import com.fixhomi.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Internal notes on customers / providers: add, edit, view. No delete. */
@Service
public class SupportNoteService {

    @Autowired
    private UserNoteRepository noteRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupportAuditService auditService;

    @Transactional
    public NoteResponse add(Long agentId, Long targetUserId, NoteRequest req) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", targetUserId));
        SupportCustomerService.requireCustomerOrProvider(target);

        UserNote n = new UserNote();
        n.setTargetUserId(targetUserId);
        n.setAuthorUserId(agentId);
        n.setContent(req.content().trim());
        UserNote saved = noteRepository.save(n);
        auditService.log(agentId, SupportAction.NOTE_ADDED, "NOTE", saved.getId(), "Note on user " + targetUserId);
        return NoteResponse.from(saved);
    }

    @Transactional
    public NoteResponse edit(Long agentId, Long noteId, NoteRequest req) {
        UserNote n = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Note", "id", noteId));
        n.setContent(req.content().trim());
        UserNote saved = noteRepository.save(n);
        auditService.log(agentId, SupportAction.NOTE_EDITED, "NOTE", noteId, "Note on user " + n.getTargetUserId());
        return NoteResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> listFor(Long targetUserId) {
        return noteRepository.findByTargetUserIdOrderByCreatedAtDesc(targetUserId)
                .stream().map(NoteResponse::from).toList();
    }
}