package com.fixhomi.auth.controller.support;

import com.fixhomi.auth.dto.support.DailyReportResponse;
import com.fixhomi.auth.dto.support.ReportRangeResponse;
import com.fixhomi.auth.service.support.SupportReportExcelWriter;
import com.fixhomi.auth.service.support.SupportReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Reports: pick a date (3 months back .. today), view it, or download it as Excel.
 * Both the view and the download use the same date check.
 */
@RestController
@RequestMapping("/api/support/reports")
public class SupportReportController {

    @Autowired
    private SupportReportService reportService;
    @Autowired
    private SupportReportExcelWriter excelWriter;

    /** The calendar calls this first to know its min and max dates. */
    @GetMapping("/range")
    public ResponseEntity<ReportRangeResponse> range() {
        return ResponseEntity.ok(reportService.range());
    }

    @GetMapping("/daily")
    public ResponseEntity<DailyReportResponse> daily(Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reportService.daily(Long.valueOf(auth.getName()), date));
    }

    @GetMapping("/daily/export")
    public ResponseEntity<byte[]> export(Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long agentId = Long.valueOf(auth.getName());
        DailyReportResponse report = reportService.daily(agentId, date); // validates the date first
        byte[] file = excelWriter.write(report);
        reportService.logExport(agentId, date);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("support-report-" + date + ".xlsx").build());
        return ResponseEntity.ok().headers(headers).body(file);
    }
}