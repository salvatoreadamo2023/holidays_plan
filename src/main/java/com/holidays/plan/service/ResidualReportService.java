package com.holidays.plan.service;

import com.holidays.plan.domain.*;
import com.holidays.plan.repo.AbsenceRequestRepository;
import com.holidays.plan.repo.LeaveBalanceRepository;
import com.holidays.plan.repo.PayrollItemRepository;
import com.holidays.plan.repo.UserRepository;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Riepilogo per l'admin: assenze calendarizzate nel periodo (ferie, TH, permessi, CO, BT, GL) incrociate con i saldi
 * reali dell'ultimo cedolino, per stimare la rimanenza e vedere chi ha pianificato abbastanza per smaltire l'AP.
 * <p>
 * Regola: il cedolino arriva in ritardo (quello di febbraio contiene le assenze fino a gennaio), quindi si sottraggono
 * le assenze dal 1° del mese del cedolino; con l'impostazione payrollCountFromNextMonth dal 1° del mese successivo.
 * Nel riepilogo i permessi (tutte le voci in ore) sono un'unica colonna, convertita in giorni da 8 ore.
 */
@Service
public class ResidualReportService {
    static final BigDecimal EIGHT = new BigDecimal("8");
    private static final BigDecimal TWELVE = new BigDecimal("12");
    private static final int MAX_MONTHS = 36;

    private final UserRepository users;
    private final AbsenceRequestRepository requests;
    private final LeaveBalanceRepository balances;
    private final PayrollItemRepository items;
    private final HolidayCalendar calendar;
    private final SettingsService settings;

    public ResidualReportService(UserRepository users, AbsenceRequestRepository requests, LeaveBalanceRepository balances,
                                 PayrollItemRepository items, HolidayCalendar calendar, SettingsService settings) {
        this.settings = settings;
        this.users = users;
        this.requests = requests;
        this.balances = balances;
        this.items = items;
        this.calendar = calendar;
    }

    public record Row(Long userId, String fullName, String employeeCode, String pm, String team,
                      // nel periodo, dal mese del cedolino in poi (o dall'inizio del periodo se il cedolino e' prima)
                      LocalDate totalsFrom,
                      BigDecimal holidayDays, BigDecimal tentativeDays, BigDecimal clientOfficeDays, BigDecimal businessTripDays,
                      BigDecimal generalLeaveDays, BigDecimal permit2hHours, BigDecimal permit4hHours, BigDecimal permitOtherHours,
                      BigDecimal permitHours, BigDecimal permitDays, BigDecimal grandTotal,
                      // saldi reali dal cedolino
                      String referenceMonth, LocalDate countFrom, BigDecimal prevLeaveDays, BigDecimal prevPermitHours,
                      BigDecimal prevPermitDays, BigDecimal prevTotalDays, BigDecimal curLeaveDays, BigDecimal curPermitHours,
                      LocalDate apDeadline,
                      // dopo il cedolino fino a fine periodo
                      BigDecimal plannedLeaveDays, BigDecimal plannedPermitHours, BigDecimal plannedTotalDays,
                      BigDecimal accruedLeaveDays, BigDecimal accruedPermitHours,
                      BigDecimal estLeaveDays, BigDecimal estPermitHours, BigDecimal estTotalDays,
                      BigDecimal apToPlanDays, Boolean apCovered, String comment) {}

    public record Report(String from, String to, LocalDate start, LocalDate end, List<Row> rows) {}

    private record Cell(String code, BigDecimal hours) {}

    /** Priorita' quando nello stesso giorno ci sono piu' voci (una TH sotto a una ferie vera non conta due volte). */
    private static int rank(AbsenceType t) {
        return switch (t) {
            case FERIE -> 0;
            case PERMESSO -> 1;
            case GENERAL_LEAVE -> 2;
            case TENTATIVE -> 3;
            case CLIENT_OFFICE -> 4;
            case BUSINESS_TRIP -> 5;
        };
    }

    static String code(AbsenceType t, BigDecimal hours) {
        return switch (t) {
            case FERIE -> "H";
            case TENTATIVE -> "TH";
            case CLIENT_OFFICE -> "CO";
            case BUSINESS_TRIP -> "BT";
            case GENERAL_LEAVE -> "GL";
            case PERMESSO -> (hours == null ? "" : hours.stripTrailingZeros().toPlainString().replace('.', ',')) + "h";
        };
    }

    public static YearMonth[] parsePeriod(String from, String to) {
        YearMonth f, t;
        try { f = YearMonth.parse(from); t = YearMonth.parse(to); } catch (Exception e) { throw ApiException.bad("Periodo non valido: usa mese-anno da / a"); }
        if (t.isBefore(f)) throw ApiException.bad("Il mese finale e' prima di quello iniziale");
        if (ChronoUnit.MONTHS.between(f, t) >= MAX_MONTHS) throw ApiException.bad("Il periodo puo' essere al massimo di " + MAX_MONTHS + " mesi");
        return new YearMonth[]{f, t};
    }

    private static final class Grid {
        final Map<Long, Map<LocalDate, Cell>> byUser = new HashMap<>();
        final Map<Long, Map<LocalDate, Integer>> rankByUser = new HashMap<>();
    }

    private Grid grid(LocalDate from, LocalDate to, Map<LocalDate, String> hol) {
        Grid g = new Grid();
        for (AbsenceRequest r : requests.findOverlapping(from, to, RequestStatus.BLOCKING)) {
            Long uid = r.getUser().getId();
            LocalDate s = r.getStartDate().isBefore(from) ? from : r.getStartDate();
            LocalDate e = r.getEndDate().isAfter(to) ? to : r.getEndDate();
            for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
                if (HolidayCalendar.isWeekend(d) || hol.containsKey(d)) continue;
                int rk = rank(r.getType());
                Map<LocalDate, Integer> ranks = g.rankByUser.computeIfAbsent(uid, k -> new HashMap<>());
                Integer old = ranks.get(d);
                if (old != null && old <= rk) continue;
                ranks.put(d, rk);
                g.byUser.computeIfAbsent(uid, k -> new HashMap<>()).put(d, new Cell(code(r.getType(), r.getHours()),
                    r.getType() == AbsenceType.PERMESSO ? r.getHours() : null));
            }
        }
        return g;
    }

    private record Sums(BigDecimal h, BigDecimal th, BigDecimal co, BigDecimal bt, BigDecimal gl, BigDecimal p2, BigDecimal p4, BigDecimal pOther) {
        BigDecimal permitHours() { return p2.add(p4).add(pOther); }
    }

    private static Sums sums(Map<LocalDate, Cell> cells, LocalDate from, LocalDate to) {
        BigDecimal h = BigDecimal.ZERO, th = BigDecimal.ZERO, co = BigDecimal.ZERO, bt = BigDecimal.ZERO, gl = BigDecimal.ZERO,
            p2 = BigDecimal.ZERO, p4 = BigDecimal.ZERO, po = BigDecimal.ZERO;
        if (cells != null && !from.isAfter(to)) for (var e : cells.entrySet()) {
            if (e.getKey().isBefore(from) || e.getKey().isAfter(to)) continue;
            Cell c = e.getValue();
            switch (c.code()) {
                case "H" -> h = h.add(BigDecimal.ONE);
                case "TH" -> th = th.add(BigDecimal.ONE);
                case "CO" -> co = co.add(BigDecimal.ONE);
                case "BT" -> bt = bt.add(BigDecimal.ONE);
                case "GL" -> gl = gl.add(BigDecimal.ONE);
                default -> {
                    BigDecimal hrs = c.hours() == null ? BigDecimal.ZERO : c.hours();
                    if (hrs.compareTo(new BigDecimal("2")) == 0) p2 = p2.add(hrs);
                    else if (hrs.compareTo(new BigDecimal("4")) == 0) p4 = p4.add(hrs);
                    else po = po.add(hrs);
                }
            }
        }
        return new Sums(h, th, co, bt, gl, p2, p4, po);
    }

    private static BigDecimal days(BigDecimal hours) {
        return hours == null ? null : hours.divide(EIGHT, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal r2(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static BigDecimal sum(List<PayrollItem> li, boolean hours, java.util.function.Function<PayrollItem, BigDecimal> f) {
        return li.stream().filter(i -> i.isHours() == hours).map(f).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String pm(User u) {
        if (u.getTeam() != null && u.getTeam().getLeader() != null && !u.getTeam().getLeader().getId().equals(u.getId()))
            return u.getTeam().getLeader().getFullName();
        if (u.getArea() != null && u.getArea().getManager() != null) return u.getArea().getManager().getFullName();
        return null;
    }

    @Transactional(readOnly = true)
    public Report report(YearMonth fromM, YearMonth toM) {
        LocalDate start = fromM.atDay(1), end = toM.atEndOfMonth();
        List<User> people = users.findByActiveTrueOrderByFullName().stream().filter(u -> u.getRole() != Role.ADMIN || u.getTeam() != null).toList();
        Map<Long, LeaveBalance> bal = balances.findAll().stream().collect(Collectors.toMap(LeaveBalance::getUserId, b -> b));
        Map<Long, List<PayrollItem>> its = items.findAll().stream().collect(Collectors.groupingBy(PayrollItem::getUserId));

        int shift = settings.payrollCountFromNextMonth() ? 1 : 0;
        LocalDate gridFrom = start;
        for (LeaveBalance b : bal.values()) {
            LocalDate cut = YearMonth.from(b.getReferenceMonth()).plusMonths(shift).atDay(1);
            if (cut.isBefore(gridFrom)) gridFrom = cut;
        }
        if (gridFrom.isBefore(end.minusYears(4))) gridFrom = end.minusYears(4);
        Map<LocalDate, String> hol = calendar.holidays(gridFrom, end);
        Grid g = grid(gridFrom, end, hol);

        List<Row> rows = new ArrayList<>();
        for (User u : people) {
            Map<LocalDate, Cell> cells = g.byUser.get(u.getId());
            LeaveBalance b = bal.get(u.getId());
            // Come nel riepilogo aziendale: i totali partono dal mese del cedolino (se cade dentro il periodo).
            LocalDate totFrom = start;
            if (b != null) {
                LocalDate cut = YearMonth.from(b.getReferenceMonth()).plusMonths(shift).atDay(1);
                if (cut.isAfter(totFrom)) totFrom = cut;
            }
            Sums p = sums(cells, totFrom, end);
            BigDecimal permitDays = days(p.permitHours());
            BigDecimal grand = p.h().add(p.th()).add(permitDays);

            List<PayrollItem> li = its.getOrDefault(u.getId(), List.of());
            String ref = null;
            LocalDate countFrom = null, deadline = null;
            BigDecimal prevLeave = null, prevPermH = null, prevPermD = null, prevTot = null, curLeave = null, curPermH = null;
            BigDecimal plLeave = null, plPermH = null, plTot = null, accL = null, accP = null, estL = null, estP = null, estT = null, apToPlan = null;
            Boolean apOk = null;
            String comment = null;
            if (b != null) {
                YearMonth rm = YearMonth.from(b.getReferenceMonth());
                ref = rm.toString();
                countFrom = rm.plusMonths(shift).atDay(1);
                if (!li.isEmpty()) {
                    // voci del cedolino a piena precisione (5 decimali), come nei conti aziendali
                    prevLeave = sum(li, false, PayrollItem::getPrevBalance);
                    curLeave = sum(li, false, PayrollItem::getCurBalance);
                    prevPermH = sum(li, true, PayrollItem::getPrevBalance);
                    curPermH = sum(li, true, PayrollItem::getCurBalance);
                } else {
                    prevLeave = nz(b.getPrevLeaveDays());
                    prevPermH = nz(b.getPrevPermitHours());
                    curLeave = nz(b.getLeaveDays());
                    curPermH = nz(b.getPermitHours());
                }
                prevPermD = days(prevPermH);
                prevTot = prevLeave.add(prevPermD);
                deadline = li.stream().map(PayrollItem::getDeadline).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);

                Sums after = sums(cells, countFrom, end);
                plLeave = after.h().add(after.th());
                plPermH = after.permitHours();
                plTot = plLeave.add(days(plPermH));
                // maturazione mensile (rateo annuale / 12) dai mesi dopo il cedolino fino a fine periodo:
                // il maturato del cedolino arriva fino al suo mese compreso (MESE 10 = 10/12 dell'anno).
                long months = Math.max(0, ChronoUnit.MONTHS.between(rm, toM));
                BigDecimal m = BigDecimal.valueOf(months);
                accL = li.stream().filter(i -> !i.isHours() && i.getAnnualQty() != null).map(i -> i.getAnnualQty().multiply(m).divide(TWELVE, 4, RoundingMode.HALF_UP)).reduce(BigDecimal.ZERO, BigDecimal::add);
                accP = li.stream().filter(i -> i.isHours() && i.getAnnualQty() != null).map(i -> i.getAnnualQty().multiply(m).divide(TWELVE, 4, RoundingMode.HALF_UP)).reduce(BigDecimal.ZERO, BigDecimal::add);
                // ferie e permessi in giorni: dal calendario non si sa su quale voce il cedolino scalera' una giornata,
                // quindi la stima vera e' quella totale; le due parziali sottraggono ferie/TH dalle ferie e i permessi dai permessi.
                estL = prevLeave.add(curLeave).add(accL).subtract(plLeave);
                estP = prevPermH.add(curPermH).add(accP).subtract(plPermH);
                estT = estL.add(days(estP));
                BigDecimal missing = prevTot.subtract(plTot);
                apToPlan = missing.signum() > 0 ? missing : BigDecimal.ZERO;
                apOk = missing.signum() <= 0;
                if (!apOk) comment = (plTot.signum() == 0 ? "Nessuna pianificazione" : "Ha pianificato " + fmt(plTot) + " gg") + " ma ha " + fmt(prevTot) + " gg di AP (ferie + permessi)";
            } else {
                comment = "Nessun cedolino caricato";
            }
            rows.add(new Row(u.getId(), u.getFullName(), u.getEmployeeCode(), pm(u), u.getTeam() == null ? null : u.getTeam().getName(),
                totFrom, p.h(), p.th(), p.co(), p.bt(), p.gl(), p.p2(), p.p4(), p.pOther(), r2(p.permitHours()), r2(permitDays), r2(grand),
                ref, countFrom, r2(prevLeave), r2(prevPermH), r2(prevPermD), r2(prevTot), r2(curLeave), r2(curPermH), deadline,
                r2(plLeave), r2(plPermH), r2(plTot), r2(accL), r2(accP), r2(estL), r2(estP), r2(estT), r2(apToPlan), apOk, comment));
        }
        rows.sort(Comparator.comparing((Row r) -> r.team() == null ? "~" : r.team(), String.CASE_INSENSITIVE_ORDER).thenComparing(Row::fullName, String.CASE_INSENSITIVE_ORDER));
        return new Report(fromM.toString(), toM.toString(), start, end, rows);
    }

    private static String fmt(BigDecimal v) {
        return r2(v).toPlainString().replace('.', ',');
    }

    // ---------------- export Excel ----------------

    private static final Map<String, String> FILL = Map.of("H", "FFFF00", "TH", "ED7D31", "CO", "C6E0B4", "BT", "BDD7EE", "GL", "D9B3FF");
    private static final String PERMIT_FILL = "92D050", OFF_FILL = "D9D9D9";

    @Transactional(readOnly = true)
    public byte[] export(YearMonth fromM, YearMonth toM) {
        Report rep = report(fromM, toM);
        LocalDate start = rep.start(), end = rep.end();
        Map<LocalDate, String> hol = calendar.holidays(start, end);
        Grid g = grid(start, end, hol);
        List<LocalDate> days = start.datesUntil(end.plusDays(1)).toList();
        DateTimeFormatter dm = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Workbook wb = new Workbook(out, "GestioneFerie", "1.0");
            Worksheet ws = wb.newWorksheet("Riepilogo");
            ws.value(0, 0, "Residuo pianificato ferie e permessi - dal " + start.format(dm) + " al " + end.format(dm));
            ws.style(0, 0).bold().fontSize(13).set();
            ws.value(1, 0, "I totali (H, TH, CO, permessi, GRAND TOTAL) partono dalla data 'Conteggio dal' se cade nel periodo. Legenda: H = ferie, TH = ferie ipotetiche, 2h/4h = permessi, CO = presso cliente, BT = trasferta, GL = general leave, F = festivo/chiuso. "
                + "Stima: saldo del cedolino meno ferie, TH e permessi dalla data 'Conteggio dal' a fine periodo, piu' la maturazione mensile.");
            int hr = 3, c0 = 3;
            ws.value(hr, 0, "PM");
            ws.value(hr, 1, "Team");
            ws.value(hr, 2, "Nome");
            for (int i = 0; i < days.size(); i++) {
                LocalDate d = days.get(i);
                ws.value(hr - 1, c0 + i, d.getDayOfMonth() == 1 || i == 0 ? d.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ITALIAN)).toUpperCase(Locale.ITALIAN) : "");
                ws.value(hr, c0 + i, d.getDayOfMonth());
                boolean off = HolidayCalendar.isWeekend(d) || hol.containsKey(d);
                ws.value(hr + 1, c0 + i, off ? "F" : "W");
                ws.width(c0 + i, 4.2);
                if (off) ws.range(hr, c0 + i, hr + 1, c0 + i).style().fillColor(OFF_FILL).set();
            }
            String[] tot = {"Holiday Total (H)", "TH Total", "CO Total", "2H Permit (ore)", "4H Permit (ore)", "Altri permessi (ore)", "Permit (Day)",
                "GRAND TOTAL (H+TH+Permessi)", "Cedolino", "Conteggio dal", "ESERCIZIO PREC. - FERIE", "ESERCIZIO PREC. - PERM. (gg)",
                "ESERCIZIO PREC. - FERIE + PERM.", "ESERCIZIO IN CORSO - FERIE", "ESERCIZIO IN CORSO - PERM. (ore)", "Scadenza AP",
                "Pianificato dopo il cedolino (gg)", "AP ancora da pianificare (gg)", "Stima ferie a fine periodo (gg)",
                "Stima permessi a fine periodo (ore)", "Stima totale a fine periodo (gg)", "In regola", "Commenti"};
            int ct = c0 + days.size();
            for (int i = 0; i < tot.length; i++) { ws.value(hr, ct + i, tot[i]); ws.width(ct + i, i == tot.length - 1 ? 50 : 14); }
            ws.range(hr, 0, hr, ct + tot.length - 1).style().bold().wrapText(true).set();
            ws.range(hr, ct, hr, ct + tot.length - 1).style().bold().wrapText(true).fillColor("EDE9FE").set();
            ws.rowHeight(hr, 58);
            ws.width(0, 22);
            ws.width(1, 16);
            ws.width(2, 26);
            ws.freezePane(3, hr + 2);
            int row = hr + 2;
            for (Row r : rep.rows()) {
                ws.value(row, 0, r.pm() == null ? "" : r.pm());
                ws.value(row, 1, r.team() == null ? "" : r.team());
                ws.value(row, 2, r.fullName().toUpperCase(Locale.ITALIAN));
                Map<LocalDate, Cell> cells = g.byUser.getOrDefault(r.userId(), Map.of());
                for (int i = 0; i < days.size(); i++) {
                    LocalDate d = days.get(i);
                    Cell c = cells.get(d);
                    if (c != null) {
                        ws.value(row, c0 + i, c.code());
                        ws.style(row, c0 + i).fillColor(c.hours() != null ? PERMIT_FILL : FILL.getOrDefault(c.code(), "FFFFFF")).horizontalAlignment("center").set();
                    } else if (HolidayCalendar.isWeekend(d) || hol.containsKey(d)) {
                        ws.style(row, c0 + i).fillColor(OFF_FILL).set();
                    }
                }
                Object[] v = {r.holidayDays(), r.tentativeDays(), r.clientOfficeDays(), r.permit2hHours(), r.permit4hHours(), r.permitOtherHours(), r.permitDays(),
                    r.grandTotal(), r.referenceMonth(), r.countFrom(), r.prevLeaveDays(), r.prevPermitDays(), r.prevTotalDays(), r.curLeaveDays(), r.curPermitHours(),
                    r.apDeadline(), r.plannedTotalDays(), r.apToPlanDays(), r.estLeaveDays(), r.estPermitHours(), r.estTotalDays(),
                    r.apCovered() == null ? "" : r.apCovered() ? "SI" : "NO", r.comment()};
                for (int i = 0; i < v.length; i++) {
                    Object x = v[i];
                    if (x == null) continue;
                    if (x instanceof BigDecimal n) ws.value(row, ct + i, n);
                    else if (x instanceof LocalDate d) { ws.value(row, ct + i, d); ws.style(row, ct + i).format("dd/mm/yyyy").set(); }
                    else ws.value(row, ct + i, x.toString());
                }
                if (Boolean.FALSE.equals(r.apCovered())) ws.style(row, ct + 21).fillColor("F8CBAD").bold().set();
                row++;
            }
            wb.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
