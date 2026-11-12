package com.holidays.plan.service;

import com.holidays.plan.domain.AbsenceRequest;
import com.holidays.plan.domain.RequestStatus;
import com.holidays.plan.repo.AbsenceRequestRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Promemoria per le malattie senza certificato dopo 2 giorni. Gira ogni ora nei giorni
 * lavorativi (se il server era addormentato recupera al primo giro utile) e avvisa una volta sola.
 */
@Component
public class CertificateReminder {
    static final Duration AFTER = Duration.ofDays(2);
    private final AbsenceRequestRepository requests;
    private final Notifier notifier;
    private final AuditService audit;
    private final Clock clock;

    public CertificateReminder(AbsenceRequestRepository requests, Notifier notifier, AuditService audit, Clock clock) {
        this.requests = requests;
        this.notifier = notifier;
        this.audit = audit;
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 8-18 * * MON-FRI", zone = "Europe/Rome")
    @Transactional
    public int run() {
        Instant now = Instant.now(clock);
        List<AbsenceRequest> list = requests.sickWithoutCertificate(RequestStatus.BLOCKING, now.minus(AFTER));
        for (AbsenceRequest r : list) {
            r.setCertificateReminderAt(now);
            notifier.certificateMissing(r);
            audit.log(null, r.getId(), "CERTIFICATE_REMINDER", null);
        }
        return list.size();
    }
}
