package com.fixhomi.auth.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter for API endpoints.
 * Prevents abuse by limiting the number of requests per IP address.
 * 
 * Rate limits (per IP):
 * - Login/Register: 5 requests per minute (prevents brute force)
 * - OTP/Password Reset: 3 requests per minute (prevents spam)
 * - General API: 100 requests per minute
 */
@Component
@Order(1) // Execute before other filters
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

    // Cache of rate limit buckets per IP
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> otpBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();

    @Value("${fixhomi.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${fixhomi.rate-limit.auth.requests-per-minute:10}")
    private int authRequestsPerMinute;

    @Value("${fixhomi.rate-limit.otp.requests-per-minute:5}")
    private int otpRequestsPerMinute;

    @Value("${fixhomi.rate-limit.general.requests-per-minute:100}")
    private int generalRequestsPerMinute;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip rate limiting if disabled
        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIP(request);
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Only rate limit non-GET requests (mutations)
        if ("GET".equals(method) && !path.contains("/verify")) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = resolveBucket(clientIp, path);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            logger.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);
            sendRateLimitResponse(response, path);
        }
    }

    /**
     * Resolve appropriate rate limit bucket based on endpoint.
     */
    private Bucket resolveBucket(String clientIp, String path) {
        if (isAuthEndpoint(path)) {
            return authBuckets.computeIfAbsent(clientIp, this::createAuthBucket);
        } else if (isOtpEndpoint(path)) {
            return otpBuckets.computeIfAbsent(clientIp, this::createOtpBucket);
        } else {
            return generalBuckets.computeIfAbsent(clientIp, this::createGeneralBucket);
        }
    }

    private boolean isAuthEndpoint(String path) {
        return path.contains("/login") ||
               path.contains("/register") ||
               path.contains("/oauth2/google") ||
               path.contains("/token/validate");
    }

    private boolean isOtpEndpoint(String path) {
        return path.contains("/otp") ||
               path.contains("/forgot-password") ||
               path.contains("/send-verification") ||
               path.contains("/resend");
    }

    /**
     * Create rate limit bucket for auth endpoints.
     * Stricter limits to prevent brute force attacks.
     */
    private Bucket createAuthBucket(String key) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(authRequestsPerMinute)
                        .refillGreedy(authRequestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /**
     * Create rate limit bucket for OTP/verification endpoints.
     * Very strict to prevent SMS/email spam.
     */
    private Bucket createOtpBucket(String key) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(otpRequestsPerMinute)
                        .refillGreedy(otpRequestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /**
     * Create rate limit bucket for general API endpoints.
     */
    private Bucket createGeneralBucket(String key) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(generalRequestsPerMinute)
                        .refillGreedy(generalRequestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /**
     * Get client IP address safely for rate limiting.
     *
     * On Render.com, the reverse proxy sets X-Forwarded-For but any client can
     * also inject that header. Using request.getRemoteAddr() gives us the actual
     * TCP connection IP (Render's proxy), which is safe and un-spoofable.
     *
     * We only fall back to X-Forwarded-For when remoteAddr is a private/loopback
     * address, meaning we're running behind a local dev proxy. In that case we
     * take the rightmost non-private IP (the one the trusted proxy saw).
     */
    private String getClientIP(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        // In production (Render), remoteAddr is the proxy's public IP or the
        // container-network IP that Render controls — use it directly.
        if (!isPrivateOrLoopback(remoteAddr)) {
            return remoteAddr;
        }

        // Behind a local/dev proxy — extract IP from X-Forwarded-For.
        // Take the rightmost non-private IP (last hop before our trusted proxy).
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String[] ips = xForwardedFor.split(",");
            for (int i = ips.length - 1; i >= 0; i--) {
                String ip = ips[i].trim();
                if (!ip.isEmpty() && !isPrivateOrLoopback(ip)) {
                    return ip;
                }
            }
            // All IPs in the chain are private — use the first one
            return ips[0].trim();
        }

        return remoteAddr;
    }

    /**
     * Check if an IP address is a private (RFC 1918) or loopback address.
     */
    private boolean isPrivateOrLoopback(String ip) {
        if (ip == null || ip.isEmpty()) return true;
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return true;
        }
        // IPv4 private ranges
        if (ip.startsWith("10.")) return true;
        if (ip.startsWith("192.168.")) return true;
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Periodic cleanup of rate limit buckets to prevent unbounded memory growth.
     * Runs every 30 minutes. Clears all buckets so stale entries don't accumulate.
     */
    @Scheduled(fixedRate = 1800000)
    public void cleanupBuckets() {
        int totalSize = authBuckets.size() + otpBuckets.size() + generalBuckets.size();
        authBuckets.clear();
        otpBuckets.clear();
        generalBuckets.clear();
        logger.info("Rate limit bucket cleanup: cleared {} entries", totalSize);
    }

    /**
     * Send 429 Too Many Requests response.
     */
    private void sendRateLimitResponse(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String message = isAuthEndpoint(path) 
                ? "Too many login attempts. Please wait a minute before trying again."
                : isOtpEndpoint(path)
                ? "Too many OTP requests. Please wait before requesting again."
                : "Too many requests. Please slow down.";

        response.getWriter().write(String.format(
                "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"%s\",\"path\":\"%s\"}",
                message, path
        ));
    }
}
