package com.holidays.plan.service;

import com.holidays.plan.domain.*;
import com.holidays.plan.repo.AbsenceRequestRepository;
import com.holidays.plan.repo.LeaveBalanceRepository;
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
 * Saldi residui ufficiali (busta paga) caricati dall'admin con un Excel.
 * L'app non li calcola e non li usa per bloccare le richieste: li mostra e basta, con il mese di riferimento.
 */
@Service
public class BalanceImportService {
    private static final BigDecimal MIN_DAYS = new BigDecimal("-100"), MAX_DAYS = new BigDecimal("500");
    private static final BigDecimal MIN_HOURS = new BigDecimal("-1000"), MAX_HOURS = new BigDecimal("5000");

    private final LeaveBalanceRepository balances;
    private final UserRepository users;
    private final AbsenceRequestRepository requests;
    private final AuditService audit;
    private final Clock clock;

    public BalanceImportService(LeaveBalanceRepository balances, UserRepository users, AbsenceRequestRepository requests,
                                AuditService audit, Clock clock) {
        this.balances = balances;
        this.users = users;
        this.requests = requests;
        this.audit = audit;
        this.clock = clock;
    }

    public record BalanceRow(Long userId, String username, String fullName, String team, String referenceMonth,
                             BigDecimal leaveDays, BigDecimal permitHours, Instant uploadedAt) {}

    public record ImportRow(int row, String username, String fullName, BigDecimal leaveDays, BigDecimal permitHours, String error) {}

    public record ImportResult(String referenceMonth, boolean applied, int valid, int errors, int missing, List<ImportRow> rows) {}

    public record MyBalance(boolean available, String referenceMonth, BigDecimal leaveDays, BigDecimal permitHours,
                            Instant uploadedAt, BigDecimal leaveRequestedSince, BigDecimal permitRequestedSince) {}

    @Transactional(readOnly = true)
    public List<BalanceRow> list() {
        Map<Long, LeaveBalance> byUser = balances.findAll().stream().collect(Collectors.toMap(LeaveBalance::getUserId, Function.identity()));
        return users.findByActiveTrueOrderByFullName().stream().map(u -> {
            LeaveBalance b = byUser.get(u.getId());
            return new BalanceRow(u.getId(), u.getUsername(), u.getFullName(), u.getTeam() == null ? null : u.getTeam().getName(),
                b == null ? null : YearMonth.from(b.getReferenceMonth()).toString(),
                b == null ? null : b.getLeaveDays(), b == null ? null : b.getPermitHours(), b == null ? null : b.getUploadedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public MyBalance mine(User u) {
        Optional<LeaveBalance> ob = balances.findById(u.getId());
        if (ob.isEmpty()) return new MyBalance(false, null, null, null, null, null, null);
        LeaveBalance b = ob.get();
        LocalDate from = YearMonth.from(b.getReferenceMonth()).atEndOfMonth().plusDays(1);
        LocalDate to = from.plusYears(3);
        EnumSet<RequestStatus> st = EnumSet.copyOf(RequestStatus.ACTIVE);
        st.add(RequestStatus.PENDING);
        return new MyBalance(true, YearMonth.from(b.getReferenceMonth()).toString(), b.getLeaveDays(), b.getPermitHours(), b.getUploadedAt(),
            requests.sumAmount(u.getId(), AbsenceType.FERIE, st, from, to),
            requests.sumAmount(u.getId(), AbsenceType.PERMESSO, st, from, to));
    }

    /** Excel tipo: una riga per ogni persona attiva, con i saldi attuali gia' compilati se ci sono. */
    @Transactional(readOnly = true)
    public byte[] template() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Workbook wb = new Workbook(out, "GestioneFerie", "1.0");
            Worksheet ws = wb.newWorksheet("Saldi");
            String[] head = {"Username", "Nome e cognome", "Team", "Ferie residue (giorni)", "Permessi residui (ore)"};
            for (int c = 0; c < head.length; c++) ws.value(0, c, head[c]);
            ws.range(0, 0, 0, head.length - 1).style().bold().fillColor("E31937").fontColor("FFFFFF").set();
            ws.width(0, 18); ws.width(1, 28); ws.width(2, 18); ws.width(3, 22); ws.width(4, 22);
            ws.freezePane(0, 1);
            int r = 1;
            for (BalanceRow b : list()) {
                ws.value(r, 0, b.username());
                ws.value(r, 1, b.fullName());
                ws.value(r, 2, b.team() == null ? "" : b.team());
                if (b.leaveDays() != null) ws.value(r, 3, b.leaveDays());
                if (b.permitHours() != null) ws.value(r, 4, b.permitHours());
                r++;
            }
            Worksheet info = wb.newWorksheet("Istruzioni");
            String[] lines = {
                "Come compilare",
                "1. Inserisci per ogni persona le ferie residue (in giorni, anche con decimali: 14,5) e i permessi residui (in ore).",
                "2. Usa i valori della busta paga del mese scelto al caricamento.",
                "3. Non cambiare la colonna Username: serve a riconoscere la persona. Nome e Team sono solo di aiuto.",
                "4. Una cella vuota significa \"dato non disponibile\". Le righe senza username vengono ignorate.",
                "5. Il saldo e' solo informativo: non blocca le richieste di ferie o permessi.",
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
        YearMonth ym;
        try { ym = YearMonth.parse(month); } catch (Exception e) { throw ApiException.bad("Mese di riferimento non valido"); }
        YearMonth now = YearMonth.now(clock);
        if (ym.isAfter(now) || ym.isBefore(now.minusYears(2))) throw ApiException.bad("Il mese di riferimento deve essere tra gli ultimi due anni");
        if (file == null || file.isEmpty()) throw ApiException.bad("Scegli il file Excel da caricare");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) throw ApiException.bad("Il file deve essere un Excel .xlsx (usa il modello scaricabile)");

        List<List<String>> sheet;
        try (InputStream in = file.getInputStream()) {
            sheet = XlsxReader.firstSheet(in);
        } catch (IOException e) {
            throw ApiException.bad("File Excel non leggibile: usa il modello scaricabile");
        }

        int headerRow = -1, cUser = -1, cLeave = -1, cPermit = -1;
        for (int r = 0; r < Math.min(sheet.size(), 20) && headerRow < 0; r++) {
            List<String> row = sheet.get(r);
            for (int c = 0; c < row.size(); c++) {
                String h = row.get(c).toLowerCase(Locale.ROOT);
                if (h.startsWith("username")) cUser = c;
                else if (h.startsWith("ferie")) cLeave = c;
                else if (h.startsWith("permess")) cPermit = c;
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
            BigDecimal leave = null, permit = null;
            if (u == null) err = "Username sconosciuto o persona disattivata";
            else if (!seen.add(u.getId())) err = "Persona ripetuta: vale la prima riga";
            else {
                try {
                    leave = number(cell(row, cLeave), MIN_DAYS, MAX_DAYS, "ferie");
                    permit = number(cell(row, cPermit), MIN_HOURS, MAX_HOURS, "permessi");
                } catch (IllegalArgumentException e) { err = e.getMessage(); }
            }
            rows.add(new ImportRow(r + 1, username, u == null ? null : u.getFullName(), leave, permit, err));
            if (err == null) valid.put(u.getId(), new BigDecimal[]{leave, permit});
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
                b.setUploadedAt(at);
                b.setUploadedBy(actor.getId());
                balances.save(b);
            });
            audit.log(actor.getId(), null, "BALANCES_UPLOADED", "Saldi residui " + ym + ": " + valid.size() + " persone");
        }
        return new ImportResult(ym.toString(), apply, valid.size(), errors, missing, rows);
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
