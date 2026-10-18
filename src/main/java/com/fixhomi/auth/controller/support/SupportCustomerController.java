package com.fixhomi.auth.controller.support;

import com.fixhomi.auth.dto.MessageResponse;
import com.fixhomi.auth.dto.support.*;
import com.fixhomi.auth.service.support.SupportCustomerService;
import com.fixhomi.auth.service.support.SupportNoteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Customers / providers: view, support actions, notes. */
@RestController
@RequestMapping("/api/support/customers")
public class SupportCustomerController {

    @Autowired
    private SupportCustomerService customerService;
    @Autowired
    private SupportNoteService noteService;

    @GetMapping
    public ResponseEntity<Page<SupportUserResponse>> search(@RequestParam(required = false) String q,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(customerService.search(q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportUserResponse> view(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(customerService.view(agent(auth), id));
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<MessageResponse> unlock(Authentication auth, @PathVariable Long id) {
        int n = customerService.unlock(agent(auth), id);
        return ResponseEntity.ok(new MessageResponse(n + " lock(s) cleared"));
    }

    @PostMapping("/{id}/force-logout")
    public ResponseEntity<MessageResponse> forceLogout(Authentication auth, @PathVariable Long id) {
        int n = customerService.forceLogout(agent(auth), id);
        return ResponseEntity.ok(new MessageResponse("Signed out everywhere (" + n + " item(s) revoked)"));
    }

    @PostMapping("/{id}/resend-verification-email")
    public ResponseEntity<MessageResponse> resend(Authentication auth, @PathVariable Long id) {
        String masked = customerService.resendVerificationEmail(agent(auth), id);
        return ResponseEntity.ok(new MessageResponse("Verification email sent to " + masked));
    }

    /** Needs the BLOCK_ACCOUNTS permission, otherwise 403. */
    @PostMapping("/{id}/block")
    public ResponseEntity<MessageResponse> block(Authentication auth, @PathVariable Long id,
                                                 @Valid @RequestBody BlockRequest req) {
        customerService.block(agent(auth), id, req.reason());
        return ResponseEntity.ok(new MessageResponse("Account blocked"));
    }

    @PostMapping("/{id}/unblock")
    public ResponseEntity<MessageResponse> unblock(Authentication auth, @PathVariable Long id) {
        customerService.unblock(agent(auth), id);
        return ResponseEntity.ok(new MessageResponse("Account unblocked"));
    }

    @GetMapping("/{id}/notes")
    public ResponseEntity<List<NoteResponse>> notes(@PathVariable Long id) {
        return ResponseEntity.ok(noteService.listFor(id));
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<NoteResponse> addNote(Authentication auth, @PathVariable Long id,
                                                @Valid @RequestBody NoteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noteService.add(agent(auth), id, req));
    }

    @PutMapping("/notes/{noteId}")
    public ResponseEntity<NoteResponse> editNote(Authentication auth, @PathVariable Long noteId,
                                                 @Valid @RequestBody NoteRequest req) {
        return ResponseEntity.ok(noteService.edit(agent(auth), noteId, req));
    }

    private Long agent(Authentication auth) {
        return Long.valueOf(auth.getName());
    }
}