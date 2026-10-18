package com.fixhomi.auth.service.support;

import com.fixhomi.auth.dto.support.*;
import com.fixhomi.auth.entity.*;
import com.fixhomi.auth.exception.ResourceNotFoundException;
import com.fixhomi.auth.exception.SupportBadRequestException;
import com.fixhomi.auth.repository.TicketRepository;
import com.fixhomi.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Tickets / complaints: create, view, update, assign, resolve. There is no delete on purpose. */
@Service
public class SupportTicketService {

    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupportAuditService auditService;

    @Transactional
    public TicketResponse create(Long agentId, CreateTicketRequest req) {
        User customer = userRepository.findById(req.customerUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", req.customerUserId()));
        SupportCustomerService.requireCustomerOrProvider(customer);

        Ticket t = new Ticket();
        t.setSubject(req.subject().trim());
        t.setDescription(req.description().trim());
        t.setPriority(req.priority() == null ? TicketPriority.MEDIUM : req.priority());
        t.setStatus(TicketStatus.OPEN);
        t.setCreatedByUserId(customer.getId());
        Ticket saved = ticketRepository.save(t);

        auditService.log(agentId, SupportAction.TICKET_CREATED, "TICKET", saved.getId(),
                "Ticket created for user " + customer.getId());
        return TicketResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> list(TicketStatus status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pr = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Ticket> result = status == null ? ticketRepository.findAll(pr) : ticketRepository.findByStatus(status, pr);
        return result.map(TicketResponse::from);
    }

    @Transactional(readOnly = true)
    public TicketResponse get(Long id) {
        return TicketResponse.from(find(id));
    }

    @Transactional
    public TicketResponse update(Long agentId, Long id, UpdateTicketRequest req) {
        Ticket t = find(id);
        if (t.getStatus() == TicketStatus.CLOSED) {
            throw new SupportBadRequestException("A closed ticket cannot be changed.");
        }
        if (req.status() != null) {
            if (req.status() == TicketStatus.RESOLVED) {
                throw new SupportBadRequestException("Use the resolve endpoint to resolve a ticket (it needs a note).");
            }
            t.setStatus(req.status());
        }
        if (req.priority() != null) {
            t.setPriority(req.priority());
        }
        if (req.description() != null && !req.description().isBlank()) {
            t.setDescription(req.description().trim());
        }
        Ticket saved = ticketRepository.save(t);
        auditService.log(agentId, SupportAction.TICKET_UPDATED, "TICKET", id, "Status now " + saved.getStatus());
        return TicketResponse.from(saved);
    }

    @Transactional
    public TicketResponse assignToMe(Long agentId, Long id) {
        Ticket t = find(id);
        if (t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED) {
            throw new SupportBadRequestException("This ticket is already finished.");
        }
        t.setAssignedToUserId(agentId);
        if (t.getStatus() == TicketStatus.OPEN) {
            t.setStatus(TicketStatus.IN_PROGRESS);
        }
        Ticket saved = ticketRepository.save(t);
        auditService.log(agentId, SupportAction.TICKET_ASSIGNED, "TICKET", id, "Assigned to the agent");
        return TicketResponse.from(saved);
    }

    @Transactional
    public TicketResponse resolve(Long agentId, Long id, ResolveTicketRequest req) {
        Ticket t = find(id);
        if (t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED) {
            throw new SupportBadRequestException("This ticket is already finished.");
        }
        t.setStatus(TicketStatus.RESOLVED);
        t.setResolutionNote(req.resolutionNote().trim());
        t.setResolvedAt(LocalDateTime.now());
        if (t.getAssignedToUserId() == null) {
            t.setAssignedToUserId(agentId);
        }
        Ticket saved = ticketRepository.save(t);
        auditService.log(agentId, SupportAction.TICKET_RESOLVED, "TICKET", id, "Ticket resolved");
        return TicketResponse.from(saved);
    }

    private Ticket find(Long id) {
        return ticketRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ticket", "id", id));
    }
}