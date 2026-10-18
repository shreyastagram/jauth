package com.fixhomi.auth.controller.support;

import com.fixhomi.auth.dto.support.*;
import com.fixhomi.auth.entity.TicketStatus;
import com.fixhomi.auth.service.support.SupportTicketService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Tickets: create, view, update, assign, resolve. No delete and no export. */
@RestController
@RequestMapping("/api/support/tickets")
public class SupportTicketController {

    @Autowired
    private SupportTicketService ticketService;

    @PostMapping
    public ResponseEntity<TicketResponse> create(Authentication auth, @Valid @RequestBody CreateTicketRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.create(agent(auth), req));
    }

    @GetMapping
    public ResponseEntity<Page<TicketResponse>> list(@RequestParam(required = false) TicketStatus status,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ticketService.list(status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.get(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TicketResponse> update(Authentication auth, @PathVariable Long id,
                                                 @Valid @RequestBody UpdateTicketRequest req) {
        return ResponseEntity.ok(ticketService.update(agent(auth), id, req));
    }

    @PostMapping("/{id}/assign-to-me")
    public ResponseEntity<TicketResponse> assign(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(ticketService.assignToMe(agent(auth), id));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<TicketResponse> resolve(Authentication auth, @PathVariable Long id,
                                                  @Valid @RequestBody ResolveTicketRequest req) {
        return ResponseEntity.ok(ticketService.resolve(agent(auth), id, req));
    }

    private Long agent(Authentication auth) {
        return Long.valueOf(auth.getName());
    }
}