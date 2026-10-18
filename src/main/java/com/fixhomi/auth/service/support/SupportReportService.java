package com.fixhomi.auth.service.support;

import com.fixhomi.auth.dto.support.*;
import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.SupportAction;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.SupportBadRequestException;
import com.fixhomi.auth.repository.SupportAuditLogRepository;
import com.fixhomi.auth.repository.SupportReportUserRepository;
import com.fixhomi.auth.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Builds the report for ONE chosen date. The date must be between (today - 3 months) and today.
 * The same check protects both the on-screen report and the Excel download.
 */
@Service
public class SupportReportService {

    public static final int MAX_MONTHS_BACK = 3;

    @Autowired
    private SupportReportUserRepository reportUserRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private SupportAuditLogRepository auditRepository;
    @Autowired
    private SupportAuditService auditService;

    public ReportRangeResponse range() {
        LocalDate today = LocalDate.now();
        return new ReportRangeResponse(today.minusMonths(MAX_MONTHS_BACK), today, MAX_MONTHS_BACK);
    }

    /** Throws a 400 if the date is in the future or older than 3 months. */
    public void validateDate(LocalDate date) {
        if (date == null) {
            throw new SupportBadRequestException("Please choose a date.");
        }
        LocalDate today = LocalDate.now();
        LocalDate earliest = today.minusMonths(MAX_MONTHS_BACK);
        if (date.isAfter(today)) {
            throw new SupportBadRequestException("The date cannot be in the future.");
        }
        if (date.isBefore(earliest)) {
            throw new SupportBadRequestException("Only the last " + MAX_MONTHS_BACK
                    + " months can be viewed or exported. The earliest allowed date is " + earliest + ".");
        }
    }

    @Transactional(readOnly = true)
    public DailyReportResponse daily(Long agentId, LocalDate date) {
        validateDate(date);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        List<Role> customer = List.of(Role.USER);
        List<Role> provider = List.of(Role.SERVICE_PROVIDER);

        return new DailyReportResponse(
                date, ZoneId.systemDefault().getId(),
                rows(reportUserRepository.findRegisteredBetween(customer, start, end)),
                rows(reportUserRepository.findRegisteredBetween(provider, start, end)),
                rows(reportUserRepository.findLastLoginBetween(customer, start, end)),
                rows(reportUserRepository.findLastLoginBetween(provider, start, end)),
                ticketRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end),
                auditRepository.findByActorUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        agentId, start, end).stream().map(AuditLogResponse::from).toList(),
                DailyReportResponse.NOTE);
    }

    /** Called by the controller after the Excel file was built, so the export is audited. */
    @Transactional
    public void logExport(Long agentId, LocalDate date) {
        auditService.log(agentId, SupportAction.REPORT_EXPORTED, "REPORT", null, "Daily report for " + date);
    }

    private List<ReportUserRow> rows(List<User> users) {
        return users.stream().map(u -> new ReportUserRow(
                u.getId(), u.getFullName(), u.getRole().name(),
                DataMasking.maskEmail(u.getEmail()), DataMasking.maskPhone(u.getPhoneNumber()),
                Boolean.TRUE.equals(u.getIsActive()), Boolean.TRUE.equals(u.getIsEmailVerified()),
                Boolean.TRUE.equals(u.getIsPhoneVerified()), u.getCreatedAt(), u.getLastLoginAt())).toList();
    }
}