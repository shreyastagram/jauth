package com.fixhomi.auth.service.support;

import com.fixhomi.auth.dto.support.AuditLogResponse;
import com.fixhomi.auth.dto.support.DailyReportResponse;
import com.fixhomi.auth.dto.support.ReportUserRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Turns a DailyReportResponse into an .xlsx file (as bytes).
 * Column widths are set by hand: POI's autoSizeColumn needs fonts, which a small server image may not have.
 */
@Component
public class SupportReportExcelWriter {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] USER_HEADERS = {"User ID", "Name", "Role", "Email (masked)", "Phone (masked)",
            "Active", "Email verified", "Phone verified", "Registered at", "Last login"};

    public byte[] write(DailyReportResponse report) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(wb);

            summarySheet(wb, header, report);
            userSheet(wb, header, "Registered Customers", report.registeredCustomers());
            userSheet(wb, header, "Registered Providers", report.registeredProviders());
            userSheet(wb, header, "Logged-in Customers", report.loggedInCustomers());
            userSheet(wb, header, "Logged-in Providers", report.loggedInProviders());
            actionSheet(wb, header, report.myActions());

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the Excel file", e);
        }
    }

    private void summarySheet(Workbook wb, CellStyle header, DailyReportResponse r) {
        Sheet s = wb.createSheet("Summary");
        s.setColumnWidth(0, 34 * 256);
        s.setColumnWidth(1, 30 * 256);
        Row h = s.createRow(0);
        cell(h, 0, "Item", header);
        cell(h, 1, "Value", header);
        String[][] data = {
                {"Report date", r.date().toString()},
                {"Time zone", r.timeZone()},
                {"New customers registered", String.valueOf(r.registeredCustomers().size())},
                {"New providers registered", String.valueOf(r.registeredProviders().size())},
                {"Customers last logged in", String.valueOf(r.loggedInCustomers().size())},
                {"Providers last logged in", String.valueOf(r.loggedInProviders().size())},
                {"Tickets raised (count only)", String.valueOf(r.ticketsRaised())},
                {"Note", r.note()}
        };
        int i = 1;
        for (String[] d : data) {
            Row row = s.createRow(i++);
            row.createCell(0).setCellValue(d[0]);
            row.createCell(1).setCellValue(d[1]);
        }
    }

    private void userSheet(Workbook wb, CellStyle header, String name, List<ReportUserRow> rows) {
        Sheet s = wb.createSheet(name);
        Row h = s.createRow(0);
        for (int c = 0; c < USER_HEADERS.length; c++) {
            cell(h, c, USER_HEADERS[c], header);
            s.setColumnWidth(c, (c == 1 || c == 3 ? 28 : 18) * 256);
        }
        int i = 1;
        for (ReportUserRow u : rows) {
            Row r = s.createRow(i++);
            r.createCell(0).setCellValue(u.userId());
            r.createCell(1).setCellValue(safe(u.fullName()));
            r.createCell(2).setCellValue(u.role());
            r.createCell(3).setCellValue(u.maskedEmail());
            r.createCell(4).setCellValue(u.maskedPhone());
            r.createCell(5).setCellValue(u.active() ? "Yes" : "No");
            r.createCell(6).setCellValue(u.emailVerified() ? "Yes" : "No");
            r.createCell(7).setCellValue(u.phoneVerified() ? "Yes" : "No");
            r.createCell(8).setCellValue(fmt(u.registeredAt()));
            r.createCell(9).setCellValue(fmt(u.lastLoginAt()));
        }
    }

    private void actionSheet(Workbook wb, CellStyle header, List<AuditLogResponse> rows) {
        Sheet s = wb.createSheet("My Actions");
        String[] heads = {"Time", "Action", "Target", "Target ID", "Details"};
        Row h = s.createRow(0);
        for (int c = 0; c < heads.length; c++) {
            cell(h, c, heads[c], header);
            s.setColumnWidth(c, (c == 4 ? 50 : 20) * 256);
        }
        int i = 1;
        for (AuditLogResponse a : rows) {
            Row r = s.createRow(i++);
            r.createCell(0).setCellValue(fmt(a.createdAt()));
            r.createCell(1).setCellValue(a.action());
            r.createCell(2).setCellValue(safe(a.targetType()));
            r.createCell(3).setCellValue(a.targetId() == null ? "" : String.valueOf(a.targetId()));
            r.createCell(4).setCellValue(safe(a.details()));
        }
    }

    private CellStyle headerStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        CellStyle st = wb.createCellStyle();
        st.setFont(f);
        return st;
    }

    private void cell(Row row, int col, String text, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(text);
        c.setCellStyle(style);
    }

    private String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(FMT);
    }

    private String safe(String s) {
        if (s == null) {
            return "";
        }
        // A cell starting with = + - @ can be run as a formula by Excel; a leading apostrophe-free way is a leading space.
        char first = s.isEmpty() ? ' ' : s.charAt(0);
        return (first == '=' || first == '+' || first == '-' || first == '@') ? " " + s : s;
    }
}