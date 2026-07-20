package com.fixhomi.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * One-way hashing for high-entropy link tokens (password-reset,
 * email-verification) stored at rest (M-J5).
 *
 * These tokens are 256-bit SecureRandom values looked up BY value, so a
 * read-only DB compromise previously handed an attacker live, usable tokens
 * (account takeover within their validity window). We now store SHA-256(token)
 * and look up by SHA-256(incoming); the raw token exists only in the email/link
 * sent to the user. Plain SHA-256 (no salt/stretch) is sufficient here because
 * the input already carries 256 bits of entropy — brute-force is infeasible,
 * unlike a low-entropy password.
 *
 * NOTE: intentionally NOT used for 6-digit OTPs. A 10^6 space is trivially
 * brute-forced from a DB dump, so hashing adds no real protection there; those
 * flows rely on short expiry + per-OTP attempt caps + rate limits instead.
 */
public final class TokenHasher {

    private TokenHasher() {}

    /** SHA-256 of the token, lowercase hex (64 chars). Null-safe passthrough. */
    public static String sha256Hex(String token) {
        if (token == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
