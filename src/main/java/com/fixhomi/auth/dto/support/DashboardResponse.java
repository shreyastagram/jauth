package com.fixhomi.auth.dto.support;

/** Read-only numbers for the support dashboard. */
public record DashboardResponse(long totalCustomers, long totalProviders,
                                long openTickets, long inProgressTickets, long resolvedTickets) {
}