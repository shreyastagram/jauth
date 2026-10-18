package com.fixhomi.auth.service.support;

import com.fixhomi.auth.entity.SupportAction;
import com.fixhomi.auth.entity.SupportAuditLog;
import com.fixhomi.auth.repository.SupportAuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes audit lines and lets an agent read ONLY their own lines. Nothing here edits or deletes. */
@Service
public class SupportAuditService {

    @Autowired
    private SupportAuditLogRepository auditRepository;

    @Transactional
    public void log(Long actorUserId, SupportAction action, String targetType, Long targetId, String details) {
        String safe = details != null && details.length() > 500 ? details.substring(0, 500) : details;
        auditRepository.save(new SupportAuditLog(actorUserId, action, targetType, targetId, safe));
    }

    @Transactional(readOnly = true)
    public Page<SupportAuditLog> myActivity(Long actorUserId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return auditRepository.findByActorUserId(actorUserId,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}