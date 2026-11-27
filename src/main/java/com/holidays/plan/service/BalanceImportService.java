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
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Saldi residui ufficiali (busta paga) caricati dall'admin con un Excel: il report ratei del cedolino (.xls/.xlsx,
 * con le voci separate) oppure il modello semplice dell'app. Visibili solo all'admin; servono al riepilogo
 * delle rimanenze ipotetiche. Non bloccano le richieste.
 */
@Service
public class BalanceImportService {
    private static final BigDecimal MIN_DAYS = new BigDecimal("-100"), MAX_DAYS = new BigDecimal("500");
    private static final BigDecimal MIN_HOURS = new BigDecimal("-1000"), MAX_HOURS = new BigDecimal("5000");

    private final LeaveBalanceRepository balances;
    private final UserRepository users;
    private final PayrollItemRepository items;
    private final AuditService audit;
    private final Clock clock;

    public BalanceImportService(LeaveBalanceRepository balances, UserRepository users, PayrollItemRepository items,
                                AuditService audit, Clock clock) {
        this.balances = balances;
        this.users = users;
        this.items = items;
        this.audit = audit;
        this.clock = clock;
    }

    public record BalanceRow(Long userId, String username, String fullName, String team, String referenceMonth,
                             BigDecimal leaveDays, BigDecimal permitHours, BigDecimal prevLeaveDays, BigDecimal prevPermitHours,
                             Instant uploadedAt, String employeeCode, List<ItemView> items) {}

    /** Una voce del cedolino in anteprima: unita' G (giorni) o H (ore). */
    public record ItemView(String code, String description, String unit, BigDecimal annual, BigDecimal prevBalance,
                           BigDecimal curBalance, BigDecimal totalResidual, BigDecimal totalProjected, LocalDate deadline) {
        static ItemView of(PayrollItem i) {
            return new ItemView(i.getCode(), i.getDescription(), i.getUnit(), i.getAnnualQty(), i.getPrevBalance(), i.getCurBalance(),
                i.getTotalResidual(), i.getTotalProjected(), i.getDeadline());
        }
    }

    public record ImportRow(int row, String username, String fullName, BigDecimal leaveDays, BigDecimal permitHours,
                            BigDecimal prevLeaveDays, BigDecimal prevPermitHours, String error,
                            String employeeCode, String referenceMonth, List<ItemView> items, String note) {
        ImportRow(int row, String username, String fullName, BigDecimal leaveDays, BigDecimal permitHours,
                  BigDecimal prevLeaveDays, BigDecimal prevPermitHours, String error) {
            this(row, username, fullName, leaveDays, permitHours, prevLeaveDays, prevPermitHours, error, null, null, null, null);
        }
    }

    /** format: PAYROLL (report ratei del cedolino) o TEMPLATE (modello semplice). */
    public record ImportResult(String referenceMonth, boolean applied, int valid, int errors, int missing, List<ImportRow> rows, String format) {}

    @Transactional(readOnly = true)
    public List<BalanceRow> list() {
        Map<Long, LeaveBalance> byUser = balances.findAll().stream().collect(Collectors.toMap(LeaveBalance::getUserId, Function.identity()));
        Map<Long, List<ItemView>> itemsByUser = items.findAllByOrderByUserIdAscCodeAsc().stream()
            .collect(Collectors.groupingBy(PayrollItem::getUserId, Collectors.mapping(ItemView::of, Collectors.toList())));
        return users.findByActiveTrueOrderByFullName().stream().map(u -> {
            LeaveBalance b = byUser.get(u.getId());
            return new BalanceRow(u.getId(), u.getUsername(), u.getFullName(), u.getTeam() == null ? null : u.getTeam().getName(),
                b == null ? null : YearMonth.from(b.getReferenceMonth()).toString(),
                b == null ? null : b.getLeaveDays(), b == null ? null : b.getPermitHours(),
                b == null ? null : b.getPrevLeaveDays(), b == null ? null : b.getPrevPermitHours(), b == null ? null : b.getUploadedAt(),
                u.getEmployeeCode(), itemsByUser.getOrDefault(u.getId(), List.of()));
        }).toList();
    }

    /** Excel tipo: una riga per ogni persona attiva, con i saldi attuali gia' compilati se ci sono. */
    @Transactional(readOnly = true)
    public byte[] template() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Workbook wb = new Workbook(out, "GestioneFerie", "1.0");
            Worksheet ws = wb.newWorksheet("Saldi");
            String[] head = {"Username", "Nome e cognome", "Team", "Ferie residue (giorni)", "Permessi residui (ore)",
                "Ferie AP (giorni)", "Permessi AP (ore)"};
            for (int c = 0; c < head.length; c++) ws.value(0, c, head[c]);
            ws.range(0, 0, 0, head.length - 1).style().bold().fillColor("E31937").fontColor("FFFFFF").set();
            ws.width(0, 18); ws.width(1, 28); ws.width(2, 18); ws.width(3, 22); ws.width(4, 22); ws.width(5, 20); ws.width(6, 20);
            ws.freezePane(0, 1);
            int r = 1;
            for (BalanceRow b : list()) {
                ws.value(r, 0, b.username());
                ws.value(r, 1, b.fullName());
                ws.value(r, 2, b.team() == null ? "" : b.team());
                if (b.leaveDays() != null) ws.value(r, 3, b.leaveDays());
                if (b.permitHours() != null) ws.value(r, 4, b.permitHours());
                if (b.prevLeaveDays() != null) ws.value(r, 5, b.prevLeaveDays());
                if (b.prevPermitHours() != null) ws.value(r, 6, b.prevPermitHours());
                r++;
            }
            Worksheet info = wb.newWorksheet("Istruzioni");
            String[] lines = {
                "Come compilare",
                "1. Inserisci per ogni persona le ferie residue (in giorni, anche con decimali: 14,5) e i permessi residui (in ore).",
                "2. Usa i valori della busta paga del mese scelto al caricamento.",
                "3. Non cambiare la colonna Username: serve a riconoscere la persona. Nome e Team sono solo di aiuto.",
                "4. Una cella vuota significa \"dato non disponibile\". Le righe senza username vengono ignorate.",
                "5. Le colonne AP (anno precedente) sono facoltative: indica il residuo dell'anno scorso ancora da godere, se la busta paga lo riporta a parte.",
                "6. Il saldo e' solo informativo: non blocca le richieste di ferie o permessi.",
            };
            for (int i = 0; i < lines.length; i++) info.value(i, 0, lines[i]);
            info.style(0, 0).bold().fontSize(13).set();
            info.width(0, 110);
            wb.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Legge l'Excel; con apply=false e' solo un'anteprima, con apply=true salva le righe valide. */
    @Transactional
    public ImportResult importFile(User actor, MultipartFile file, String month, boolean apply) {
        if (file == null || file.isEmpty()) throw ApiException.bad("Scegli il file Excel da caricare");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls"))
            throw ApiException.bad("Il file deve essere un Excel (.xls o .xlsx): il report ratei del cedolino o il modello scaricabile");

        List<List<String>> sheet;
        try (InputStream in = file.getInputStream()) {
            sheet = SheetReader.firstSheet(in);
        } catch (IOException e) {
            throw ApiException.bad("File Excel non leggibile: usa il report ratei del cedolino o il modello scaricabile");
        }
        int payrollHeader = PayrollImport.headerRow(sheet);
        if (payrollHeader >= 0) return importPayroll(actor, sheet, payrollHeader, apply);

        YearMonth ym;
        try { ym = YearMonth.parse(month == null ? "" : month); } catch (Exception e) { throw ApiException.bad("Mese di riferimento non valido"); }
        YearMonth now = YearMonth.now(clock);
        if (ym.isAfter(now) || ym.isBefore(now.minusYears(2))) throw ApiException.bad("Il mese di riferimento deve essere tra gli ultimi due anni");

        int headerRow = -1, cUser = -1, cLeave = -1, cPermit = -1, cPrevLeave = -1, cPrevPermit = -1;
        for (int r = 0; r < Math.min(sheet.size(), 20) && headerRow < 0; r++) {
            List<String> row = sheet.get(r);
            for (int c = 0; c < row.size(); c++) {
                String h = row.get(c).toLowerCase(Locale.ROOT);
                boolean ap = h.matches(".*(^|\\W)ap(\\W|$).*") || h.contains("a.p.") || h.contains("anno prec");
                if (h.startsWith("username")) cUser = c;
                else if (h.startsWith("ferie")) { if (ap) cPrevLeave = c; else cLeave = c; }
                else if (h.startsWith("permess")) { if (ap) cPrevPermit = c; else cPermit = c; }
            }
            if (cUser >= 0) headerRow = r;
        }
        if (headerRow < 0 || (cLeave < 0 && cPermit < 0))
            throw ApiException.bad("Intestazioni non trovate: servono le colonne Username, Ferie residue e Permessi residui");

        Map<String, User> byUsername = users.findByActiveTrueOrderByFullName().stream()
            .collect(Collectors.toMap(u -> u.getUsername().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        List<ImportRow> rows = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Map<Long, BigDecimal[]> valid = new LinkedHashMap<>();
        for (int r = headerRow + 1; r < sheet.size(); r++) {
            List<String> row = sheet.get(r);
            String username = cell(row, cUser);
            if (username.isEmpty()) continue;
            User u = byUsername.get(username.toLowerCase(Locale.ROOT));
            String err = null;
            BigDecimal leave = null, permit = null, prevLeave = null, prevPermit = null;
            if (u == null) err = "Username sconosciuto o persona disattivata";
            else if (!seen.add(u.getId())) err = "Persona ripetuta: vale la prima riga";
            else {
                try {
                    leave = number(cell(row, cLeave), MIN_DAYS, MAX_DAYS, "ferie");
                    permit = number(cell(row, cPermit), MIN_HOURS, MAX_HOURS, "permessi");
                    prevLeave = number(cell(row, cPrevLeave), MIN_DAYS, MAX_DAYS, "ferie AP");
                    prevPermit = number(cell(row, cPrevPermit), MIN_HOURS, MAX_HOURS, "permessi AP");
                } catch (IllegalArgumentException e) { err = e.getMessage(); }
            }
            rows.add(new ImportRow(r + 1, username, u == null ? null : u.getFullName(), leave, permit, prevLeave, prevPermit, err));
            if (err == null) valid.put(u.getId(), new BigDecimal[]{leave, permit, prevLeave, prevPermit});
            if (rows.size() > 2000) throw ApiException.bad("Troppe righe nel file");
        }
        int errors = (int) rows.stream().filter(x -> x.error() != null).count();
        int missing = (int) byUsername.values().stream().filter(u -> !valid.containsKey(u.getId())).count();
        if (apply) {
            if (valid.isEmpty()) throw ApiException.bad("Nessuna riga valida da caricare");
            Instant at = Instant.now(clock);
            valid.forEach((uid, v) -> {
                LeaveBalance b = balances.findById(uid).orElseGet(LeaveBalance::new);
                b.setUserId(uid);
                b.setReferenceMonth(ym.atDay(1));
                b.setLeaveDays(v[0]);
                b.setPermitHours(v[1]);
                b.setPrevLeaveDays(v[2]);
                b.setPrevPermitHours(v[3]);
                b.setUploadedAt(at);
                b.setUploadedBy(actor.getId());
                balances.save(b);
            });
            audit.log(actor.getId(), null, "BALANCES_UPLOADED", "Saldi residui " + ym + ": " + valid.size() + " persone");
        }
        return new ImportResult(ym.toString(), apply, valid.size(), errors, missing, rows, "TEMPLATE");
    }

    /**
     * Report ratei del cedolino: le righe di una stessa matricola diventano le voci di quella persona.
     * Persona riconosciuta dalla matricola; se nessuno ce l'ha, da cognome e nome (e la matricola viene salvata).
     */
    private ImportResult importPayroll(User actor, List<List<String>> sheet, int headerRow, boolean apply) {
        List<PayrollImport.Line> lines = PayrollImport.parse(sheet, headerRow);
        List<User> active = users.findByActiveTrueOrderByFullName();
        Map<String, User> byCode = new HashMap<>();
        Map<String, List<User>> byName = new HashMap<>();
        for (User u : active) {
            if (u.getEmployeeCode() != null) byCode.put(u.getEmployeeCode(), u);
            byName.computeIfAbsent(PayrollImport.person(u.getFullName()), k -> new ArrayList<>()).add(u);
        }
        Map<String, List<PayrollImport.Line>> groups = new LinkedHashMap<>();
        List<ImportRow> rows = new ArrayList<>();
        for (PayrollImport.Line l : lines) {
            if (l.code() == null) {
                rows.add(new ImportRow(l.row(), "", (l.surname() + " " + l.name()).trim(), null, null, null, null, l.error(), null, null, null, null));
                continue;
            }
            groups.computeIfAbsent(l.code(), k -> new ArrayList<>()).add(l);
        }
        Map<Long, List<PayrollImport.Line>> valid = new LinkedHashMap<>();
        Map<Long, String> codesToSet = new HashMap<>();
        Map<String, Integer> months = new HashMap<>();
        for (var g : groups.entrySet()) {
            List<PayrollImport.Line> ls = g.getValue();
            PayrollImport.Line first = ls.get(0);
            String fileName = (first.surname() + " " + first.name()).trim();
            User u = byCode.get(g.getKey());
            String err = ls.stream().map(PayrollImport.Line::error).filter(Objects::nonNull).findFirst()
                .map(e -> "Riga " + ls.stream().filter(x -> x.error() != null).findFirst().get().row() + ": " + e).orElse(null);
            String note = null;
            if (u == null && err == null) {
                List<User> c = new ArrayList<>(byName.getOrDefault(PayrollImport.person(first.surname() + " " + first.name()), List.of()));
                c.addAll(byName.getOrDefault(PayrollImport.person(first.name() + " " + first.surname()), List.of()));
                c = c.stream().distinct().toList();
                if (c.size() == 1 && c.get(0).getEmployeeCode() == null) {
                    u = c.get(0);
                    codesToSet.put(u.getId(), g.getKey());
                    note = "Riconosciuta dal nome: le verra' assegnata la matricola " + g.getKey();
                } else if (c.size() == 1) {
                    err = "Nell'app " + c.get(0).getFullName() + " ha la matricola " + c.get(0).getEmployeeCode() + ", nel file " + g.getKey();
                } else if (c.size() > 1) {
                    err = "Matricola " + g.getKey() + " sconosciuta e piu' persone con questo nome: inserisci la matricola nella scheda della persona";
                } else {
                    err = "Matricola " + g.getKey() + " sconosciuta: inseriscila nella scheda della persona";
                }
            }
            if (err == null && ls.stream().map(PayrollImport.Line::month).distinct().count() > 1) err = "Mesi diversi per la stessa persona";
            if (err == null && ls.stream().map(PayrollImport.Line::rateCode).distinct().count() < ls.size()) err = "Codice rateo ripetuto per la stessa persona";
            BigDecimal[] agg = aggregate(ls);
            List<ItemView> views = ls.stream().map(l -> new ItemView(l.rateCode(), l.rateDesc(), l.unit(), l.annual(), l.prevBalance(),
                l.curBalance(), l.totalResidual(), l.totalProjected(), l.deadline())).toList();
            String ym = first.month() == null ? null : first.month().toString();
            rows.add(new ImportRow(first.row(), u == null ? "" : u.getUsername(), u == null ? fileName : u.getFullName(),
                agg[0], agg[1], agg[2], agg[3], err, g.getKey(), ym, views, note));
            if (err == null) {
                valid.put(u.getId(), ls);
                months.merge(ym, 1, Integer::sum);
            }
        }
        int errors = (int) rows.stream().filter(x -> x.error() != null).count();
        int missing = (int) active.stream().filter(u -> !valid.containsKey(u.getId())).count();
        String refMonth = months.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        if (apply) {
            if (valid.isEmpty()) throw ApiException.bad("Nessuna persona valida da caricare");
            Instant at = Instant.now(clock);
            Map<Long, User> byId = active.stream().collect(Collectors.toMap(User::getId, Function.identity()));
            valid.forEach((uid, ls) -> {
                LocalDate month = ls.get(0).month().atDay(1);
                items.deleteByUser(uid);
                items.flush();
                for (PayrollImport.Line l : ls) {
                    PayrollItem i = new PayrollItem();
                    i.setUserId(uid);
                    i.setCode(l.rateCode());
                    i.setDescription(l.rateDesc() == null || l.rateDesc().isEmpty() ? null : l.rateDesc().substring(0, Math.min(100, l.rateDesc().length())));
                    i.setUnit(l.unit());
                    i.setReferenceMonth(month);
                    i.setAnnualQty(l.annual());
                    i.setPrevResidual(l.prevResidual());
                    i.setPrevUsed(l.prevUsed());
                    i.setPrevBalance(l.prevBalance());
                    i.setCurAccrued(l.curAccrued());
                    i.setCurUsed(l.curUsed());
                    i.setCurBalance(l.curBalance());
                    i.setTotalResidual(l.totalResidual());
                    i.setStillAccruable(l.stillAccruable());
                    i.setTotalProjected(l.totalProjected());
                    i.setNonCompensable(l.nonCompensable());
                    i.setDeadline(l.deadline());
                    i.setUploadedAt(at);
                    items.save(i);
                }
                BigDecimal[] agg = aggregate(ls);
                LeaveBalance b = balances.findById(uid).orElseGet(LeaveBalance::new);
                b.setUserId(uid);
                b.setReferenceMonth(month);
                b.setLeaveDays(agg[0]);
                b.setPermitHours(agg[1]);
                b.setPrevLeaveDays(agg[2]);
                b.setPrevPermitHours(agg[3]);
                b.setUploadedAt(at);
                b.setUploadedBy(actor.getId());
                balances.save(b);
                String code = codesToSet.get(uid);
                if (code != null) byId.get(uid).setEmployeeCode(code);
            });
            audit.log(actor.getId(), null, "BALANCES_UPLOADED", "Ratei cedolino " + refMonth + ": " + valid.size() + " persone");
        }
        return new ImportResult(refMonth, apply, valid.size(), errors, missing, rows, "PAYROLL");
    }

    /** Totali per il saldo semplice: {ferie anno, permessi ore anno, ferie AP, permessi ore AP}. */
    private static BigDecimal[] aggregate(List<PayrollImport.Line> ls) {
        BigDecimal[] a = new BigDecimal[4];
        for (PayrollImport.Line l : ls) {
            int off = "H".equals(l.unit()) ? 1 : 0;
            if (l.curBalance() != null) a[off] = (a[off] == null ? BigDecimal.ZERO : a[off]).add(l.curBalance());
            if (l.prevBalance() != null) a[off + 2] = (a[off + 2] == null ? BigDecimal.ZERO : a[off + 2]).add(l.prevBalance());
        }
        for (int i = 0; i < 4; i++) if (a[i] != null) a[i] = a[i].setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        return a;
    }

    private static String cell(List<String> row, int c) {
        return c >= 0 && c < row.size() && row.get(c) != null ? row.get(c).trim() : "";
    }

    private static BigDecimal number(String s, BigDecimal min, BigDecimal max, String what) {
        if (s.isEmpty() || s.equals("-")) return null;
        String n = s.replace(" ", "");
        if (n.contains(",") && n.contains(".")) n = n.replace(".", "");
        n = n.replace(",", ".");
        BigDecimal v;
        try { v = new BigDecimal(n); } catch (NumberFormatException e) { throw new IllegalArgumentException("Valore non numerico per " + what + ": \"" + s + "\""); }
        if (v.compareTo(min) < 0 || v.compareTo(max) > 0) throw new IllegalArgumentException("Valore fuori scala per " + what + ": " + s);
        return v.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
