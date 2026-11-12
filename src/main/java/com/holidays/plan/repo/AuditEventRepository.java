package com.holidays.plan.repo;

import com.holidays.plan.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByRequestIdOrderByCreatedAt(Long requestId);

    List<AuditEvent> findTop300ByOrderByCreatedAtDesc();
}
