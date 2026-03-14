package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.DeleteAccountOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface DeleteAccountOtpRepository extends JpaRepository<DeleteAccountOtp, Long> {

    /** Find the latest valid (unused, not expired) OTP for a phone number */
    @Query("SELECT o FROM DeleteAccountOtp o WHERE o.phoneNumber = :phone " +
           "AND o.used = false AND o.expiresAt > :now ORDER BY o.createdAt DESC LIMIT 1")
    Optional<DeleteAccountOtp> findLatestValidOtp(@Param("phone") String phone, @Param("now") LocalDateTime now);

    /** Invalidate all OTPs for a phone number */
    @Modifying
    @Query("UPDATE DeleteAccountOtp o SET o.used = true WHERE o.phoneNumber = :phone AND o.used = false")
    int invalidateAllOtpsForPhone(@Param("phone") String phone);

    /** Count recent OTP requests for rate limiting */
    @Query("SELECT COUNT(o) FROM DeleteAccountOtp o WHERE o.phoneNumber = :phone AND o.createdAt > :since")
    long countRecentRequests(@Param("phone") String phone, @Param("since") LocalDateTime since);

    /** Cleanup expired entries */
    @Modifying
    @Query("DELETE FROM DeleteAccountOtp o WHERE o.expiresAt < :now")
    int deleteExpiredOtps(@Param("now") LocalDateTime now);
}
