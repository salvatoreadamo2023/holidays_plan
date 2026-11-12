package com.holidays.plan.service;

import com.holidays.plan.domain.*;
import com.holidays.plan.repo.AbsenceRequestRepository;
import com.holidays.plan.repo.AttachmentRepository;
import com.holidays.plan.repo.UserRepository;
import com.holidays.plan.web.dto.AttachmentInfo;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Export mensile per HR/paghe: un foglio di riepilogo per persona e uno di dettaglio.
 * Conta solo le assenze approvate, tagliate sul mese (giorni lavorativi del mese).
 * L'Admin esporta tutti, PM e Tech Lead solo la propria area.
 */
@Service
public class ExportService {
    private static final BigDecimal EIGHT = new BigDecimal("8");
    private final AbsenceRequestRepository requests;
    private final AttachmentRepository attachments;
    private final UserRepository users;
    private final HolidayCalendar calendar;

    public ExportService(AbsenceRequestRepository requests, AttachmentRepository attachments, UserRepository users,
                         HolidayCalendar calendar) {
        this.requests = requests;
        this.attachments = attachments;
        this.users = users;
        this.calendar = calendar;
    }

    public static String fileName(YearMonth ym) {
        return "assenze_" + ym + ".xlsx";
    }

    public void checkCanExport(User actor) {
        if (!actor.getRole().isManager()) throw ApiException.forbidden();
        if (actor.getRole() != Role.ADMIN && actor.areaId() == null) throw ApiException.forbidden();
    }

    private boolean inScope(User actor, User u) {
        return actor.getRole() == Role.ADMIN || Objects.equals(actor.areaId(), u.areaId());
    }

    /** Quantita' della richiesta che cade dentro il mese: giorni (ferie/malattia) o ore (permesso). */
    BigDecimal amountInMonth(AbsenceRequest r, LocalDate from, LocalDate to) {
        if (r.getType() == AbsenceType.PERMESSO || r.isHalfDay()) {
            return r.getStartDate().isBefore(from) || r.getStartDate().isAfter(to) ? BigDecimal.ZERO : r.getAmount();
        }
        LocalDate s = r.getStartDate().isBefore(from) ? from : r.getStartDate();
        LocalDate e = r.getEndDate().isAfter(to) ? to : r.getEndDate();
        return e.isBefore(s) ? BigDecimal.ZERO : calendar.workingDays(s, e);
    }

    @Transactional(readOnly = true)
    public byte[] month(User actor, YearMonth ym) {
        checkCanExport(actor);
        LocalDate from = ym.atDay(1), to = ym.atEndOfMonth();
        List<AbsenceRequest> list = requests.findOverlapping(from, to, RequestStatus.ACTIVE).stream()
            .filter(r -> inScope(actor, r.getUser()))
            .sorted(Comparator.comparing((AbsenceRequest r) -> r.getUser().getFullName()).thenComparing(AbsenceRequest::getStartDate))
            .toList();
        Map<Long, Long> attCount = list.isEmpty() ? Map.of() : attachments
            .infoFor(list.stream().map(AbsenceRequest::getId).toList()).stream()
            .collect(Collectors.groupingBy(AttachmentInfo::requestId, Collectors.counting()));

        // Persone: tutte le attive nel perimetro, piu' chi ha assenze nel mese.
        Map<Long, User> people = new LinkedHashMap<>();
        (actor.getRole() == Role.ADMIN ? users.findByActiveTrueOrderByFullName()
            : users.findByAreaIdAndActiveTrueOrderByFullName(actor.areaId())).forEach(u -> people.put(u.getId(), u));
        list.forEach(r -> people.putIfAbsent(r.getUser().getId(), r.getUser()));

        Map<Long, BigDecimal[]> sums = new HashMap<>();
        for (AbsenceRequest r : list) {
            BigDecimal[] s = sums.computeIfAbsent(r.getUser().getId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal a = amountInMonth(r, from, to);
            switch (r.getType()) {
                case FERIE -> s[0] = s[0].add(a);
                case PERMESSO -> s[1] = s[1].add(a);
                case MALATTIA -> s[2] = s[2].add(a);
            }
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Workbook wb = new Workbook(out, "GestioneFerie", "1.0");
            String month = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ITALIAN) + " " + ym.getYear();

            Worksheet sum = wb.newWorksheet("Riepilogo");
            sum.value(0, 0, "Assenze approvate - " + month);
            sum.style(0, 0).bold().fontSize(13).set();
            String[] h = {"Persona", "Area", "Team", "Ferie (giorni)", "Permessi (ore)", "Malattia (giorni)", "Totale assenza (giorni)"};
            header(sum, 2, h);
            int row = 3;
            for (User u : people.values().stream().sorted(Comparator.comparing(User::getFullName)).toList()) {
                BigDecimal[] s = sums.getOrDefault(u.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                sum.value(row, 0, u.getFullName());
                sum.value(row, 1, u.getArea() == null ? "" : u.getArea().getName());
                sum.value(row, 2, u.getTeam() == null ? "" : u.getTeam().getName());
                sum.value(row, 3, s[0]);
                sum.value(row, 4, s[1]);
                sum.value(row, 5, s[2]);
                sum.value(row, 6, s[0].add(s[2]).add(s[1].divide(EIGHT, 1, RoundingMode.HALF_UP)));
                row++;
            }
            widths(sum, 28, 20, 24, 15, 15, 17, 22);

            Worksheet det = wb.newWorksheet("Dettaglio");
            header(det, 0, new String[]{"Persona", "Area", "Team", "Tipo", "Dal", "Al", "Mezza giornata", "Quantità nel mese", "Unità", "Allegati", "Approvata da"});
            row = 1;
            for (AbsenceRequest r : list) {
                User u = r.getUser();
                det.value(row, 0, u.getFullName());
                det.value(row, 1, u.getArea() == null ? "" : u.getArea().getName());
                det.value(row, 2, u.getTeam() == null ? "" : u.getTeam().getName());
                det.value(row, 3, Notifier.label(r.getType()));
                det.value(row, 4, r.getStartDate());
                det.style(row, 4).format("dd/mm/yyyy").set();
                det.value(row, 5, r.getEndDate());
                det.style(row, 5).format("dd/mm/yyyy").set();
                det.value(row, 6, r.isHalfDay() ? "sì" : "");
                det.value(row, 7, amountInMonth(r, from, to));
                det.value(row, 8, r.getType() == AbsenceType.PERMESSO ? "ore" : "giorni");
                det.value(row, 9, attCount.getOrDefault(r.getId(), 0L));
                det.value(row, 10, r.getDecidedBy() == null ? "" : r.getDecidedBy().getFullName());
                row++;
            }
            widths(det, 28, 20, 24, 12, 12, 12, 15, 18, 9, 10, 24);
            wb.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Export non riuscito", e);
        }
    }

    private static void header(Worksheet ws, int row, String[] h) {
        for (int i = 0; i < h.length; i++) ws.value(row, i, h[i]);
        ws.range(row, 0, row, h.length - 1).style().bold().fillColor("EDE9FE").set();
        ws.freezePane(0, row + 1);
    }

    private static void widths(Worksheet ws, double... w) {
        for (int i = 0; i < w.length; i++) ws.width(i, w[i]);
    }
}
