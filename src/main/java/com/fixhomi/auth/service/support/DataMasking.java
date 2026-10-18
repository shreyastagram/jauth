package com.fixhomi.auth.service.support;

/**
 * Hides most of an email / phone number so that support screens and exports do not spread
 * full personal data. Example: "tejswita@gmail.com" -> "te*****@gmail.com".
 */
public final class DataMasking {

    private DataMasking() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String name = email.substring(0, at);
        String domain = email.substring(at);
        String shown = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        return shown + "*".repeat(Math.max(3, name.length() - shown.length())) + domain;
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        if (phone.length() <= 4) {
            return "****";
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}