package com.holidays.plan.web;

import com.holidays.plan.domain.AbsenceRequest;
import com.holidays.plan.domain.AuditEvent;
import com.holidays.plan.domain.User;
import com.holidays.plan.repo.AbsenceRequestRepository;
import com.holidays.plan.repo.AuditEventRepository;
import com.holidays.plan.repo.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Storico delle operazioni (solo Admin: il percorso /api/admin/** e' gia' protetto). */
@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {
    private final AuditEventRepository audit;
    private final UserRepository users;
    private final AbsenceRequestRepository requests;

    public AuditController(AuditEventRepository audit, UserRepository users, AbsenceRequestRepository requests) {
        this.audit = audit;
        this.users = users;
        this.requests = requests;
    }

    public record AuditView(Long id, Instant createdAt, String action, String actorName, Long requestId,
                            String ownerName, String requestType, LocalDate startDate, LocalDate endDate, String detail) {}

    static final int DEFAULT_SIZE = 50, MAX_SIZE = 200;

    /**
     * Storico a pagine, dalla operazione piu' recente. Per la pagina successiva si passa before = id dell'ultima riga
     * ricevuta; se arrivano meno di size righe non ce ne sono altre. q cerca nel nome delle persone e nel dettaglio.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<AuditView> list(@RequestParam(required = false) Long before, @RequestParam(required = false) Integer size,
                                @RequestParam(required = false) String q) {
        int n = size == null ? DEFAULT_SIZE : Math.max(1, Math.min(MAX_SIZE, size));
        long cursor = before == null ? Long.MAX_VALUE : before;
        Pageable page = PageRequest.of(0, n);
        List<AuditEvent> events;
        if (q == null || q.isBlank()) {
            events = audit.page(cursor, page);
        } else {
            String needle = q.trim().toLowerCase(java.util.Locale.ROOT);
            List<Long> ids = new java.util.ArrayList<>(users.findAll().stream()
                .filter(u -> u.getFullName().toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || u.getUsername().toLowerCase(java.util.Locale.ROOT).contains(needle))
                .map(User::getId).toList());
            ids.add(-1L); // la lista non puo' essere vuota
            events = audit.search(cursor, "%" + needle.replace("%", "").replace("_", "") + "%", ids, page);
        }
        Set<Long> userIds = events.stream().map(AuditEvent::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> reqIds = events.stream().map(AuditEvent::getRequestId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AbsenceRequest> reqs = requests.findAllById(reqIds).stream()
            .collect(Collectors.toMap(AbsenceRequest::getId, Function.identity()));
        reqs.values().forEach(r -> userIds.add(r.getUser().getId()));
        Map<Long, String> names = users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return events.stream().map(e -> {
            AbsenceRequest r = e.getRequestId() == null ? null : reqs.get(e.getRequestId());
            return new AuditView(e.getId(), e.getCreatedAt(), e.getAction(),
                e.getActorId() == null ? "Sistema" : names.getOrDefault(e.getActorId(), "?"), e.getRequestId(),
                r == null ? null : names.get(r.getUser().getId()), r == null ? null : r.getType().name(),
                r == null ? null : r.getStartDate(), r == null ? null : r.getEndDate(), e.getDetail());
        }).toList();
    }
}
