package com.holidays.plan.service;

import com.holidays.plan.domain.AuditEvent;
import com.holidays.plan.repo.AuditEventRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditEventRepository repo;

    public AuditService(AuditEventRepository repo) {
        this.repo = repo;
    }

    public void log(Long actorId, Long requestId, String action, String detail) {
        repo.save(new AuditEvent(actorId, requestId, action, detail == null ? null
            : detail.substring(0, Math.min(500, detail.length()))));
    }
}
