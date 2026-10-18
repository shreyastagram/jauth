package com.fixhomi.auth.dto.support;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Everything the report shows for ONE chosen date.
 *
 *  registeredCustomers / registeredProviders : people who signed up that day
 *  loggedInCustomers   / loggedInProviders   : people whose latest login was that day
 *  ticketsRaised                             : how many tickets were created that day (a number only)
 *  myActions                                 : only the logged-in agent's own audit lines for that day
 */
public record DailyReportResponse(
        LocalDate date, String timeZone,
        List<ReportUserRow> registeredCustomers, List<ReportUserRow> registeredProviders,
        List<ReportUserRow> loggedInCustomers, List<ReportUserRow> loggedInProviders,
        long ticketsRaised, List<AuditLogResponse> myActions, String note) {

    public static final String NOTE =
            "Login activity shows the latest login of each person, so someone who logged in again later "
                    + "no longer appears on an earlier day. The day is counted in the server time zone.";

    public static String zoneName(ZoneId zone) {
        return zone.getId();
    }
}