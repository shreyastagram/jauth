package com.fixhomi.auth.dto.support;

import java.time.LocalDate;

/** Tells the calendar which dates may be picked (3 months back .. today). */
public record ReportRangeResponse(LocalDate earliestDate, LocalDate latestDate, int maxMonthsBack) {
}