package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.PhoneSignupOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PhoneSignupOtpRepository extends JpaRepository<PhoneSignupOtp, Long> {

    @Query("SELECT p FROM PhoneSignupOtp p WHERE p.phoneNumber = :phoneNumber " +
           "AND p.verified = false AND p.expiresAt > :now ORDER BY p.createdAt DESC LIMIT 1")
    Optional<PhoneSignupOtp> findLatestValidOtpByPhone(
            @Param("phoneNumber") String phoneNumber,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE PhoneSignupOtp p SET p.verified = true WHERE p.phoneNumber = :phoneNumber " +
           "AND p.verified = false")
    int invalidateAllForPhone(@Param("phoneNumber") String phoneNumber);

    @Modifying
    @Query("DELETE FROM PhoneSignupOtp p WHERE p.expiresAt < :now")
    int deleteExpiredOtps(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(p) FROM PhoneSignupOtp p WHERE p.phoneNumber = :phoneNumber " +
           "AND p.createdAt > :since")
    long countRecentOtpRequests(@Param("phoneNumber") String phoneNumber, @Param("since") LocalDateTime since);
}
