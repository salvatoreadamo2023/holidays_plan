package com.holidays.plan.repo;

import com.holidays.plan.domain.AuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByRequestIdOrderByCreatedAt(Long requestId);

    /** Pagina dello storico dalla piu' recente: before = id dell'ultima riga gia' mostrata. */
    @Query("select e from AuditEvent e where e.id < :before order by e.id desc")
    List<AuditEvent> page(@Param("before") long before, Pageable page);

    /** Come page, filtrando per persona (chi ha agito o di chi e' la richiesta) o per testo del dettaglio. */
    @Query("""
            select e from AuditEvent e where e.id < :before and (
              lower(e.detail) like :text or e.actorId in :userIds
              or e.requestId in (select r.id from AbsenceRequest r where r.user.id in :userIds))
            order by e.id desc""")
    List<AuditEvent> search(@Param("before") long before, @Param("text") String text,
                            @Param("userIds") Collection<Long> userIds, Pageable page);
}
