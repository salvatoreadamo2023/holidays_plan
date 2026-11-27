package com.holidays.plan.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Lettura del report ratei del cedolino ("Vacation accrual report"): una riga per persona e per voce
 * (CODICE RATEO), con matricola in DIPENDENTE e mese in MESE/ANNO. Chi ha il contratto vecchio ha piu' voci
 * di permesso (es. Perm.Ex-Fs e Rol/Par), chi ha il nuovo una sola (ExFs-Rol): restano separate.
 */
final class PayrollImport {
    private PayrollImport() {}

    record Line(int row, String code, String surname, String name, YearMonth month, String rateCode, String rateDesc, String unit,
                BigDecimal annual, BigDecimal prevResidual, BigDecimal prevUsed, BigDecimal prevBalance,
                BigDecimal curAccrued, BigDecimal curUsed, BigDecimal curBalance, BigDecimal totalResidual,
                BigDecimal stillAccruable, BigDecimal totalProjected, BigDecimal nonCompensable, LocalDate deadline, String error) {}

    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    static String key(String header) {
        String h = Normalizer.normalize(header == null ? "" : header, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return h.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
    }

    /** Riga di intestazione del report ratei, oppure -1 se il file non e' in quel formato. */
    static int headerRow(List<List<String>> sheet) {
        for (int r = 0; r < Math.min(sheet.size(), 20); r++)
            for (String c : sheet.get(r)) if (key(c).equals("CODICE RATEO")) return r;
        return -1;
    }

    /** "000080", "80" e "80.0" sono la stessa matricola; vuota = null. */
    static String normalizeCode(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.matches("\\d+(\\.0+)?")) s = s.replaceAll("\\.0+$", "").replaceFirst("^0+(?=\\d)", "");
        return s.isEmpty() ? null : s;
    }

    /** Nome confrontabile: maiuscolo, senza accenti e spazi doppi. */
    static String person(String s) {
        return key(s);
    }

    static List<Line> parse(List<List<String>> sheet, int headerRow) {
        Map<String, Integer> col = new HashMap<>();
        List<String> head = sheet.get(headerRow);
        for (int c = 0; c < head.size(); c++) col.putIfAbsent(key(head.get(c)), c);
        for (String need : List.of("DIPENDENTE", "CODICE RATEO", "TIPO RATEO", "MESE", "ANNO"))
            if (!col.containsKey(need)) throw ApiException.bad("Nel report ratei manca la colonna " + need);
        List<Line> out = new ArrayList<>();
        for (int r = headerRow + 1; r < sheet.size(); r++) {
            List<String> row = sheet.get(r);
            String code = normalizeCode(cell(row, col.get("DIPENDENTE")));
            String rate = cell(row, col.get("CODICE RATEO"));
            if (code == null && rate.isEmpty()) continue;
            String err = null;
            YearMonth ym = null;
            String unit = cell(row, col.get("TIPO RATEO")).toUpperCase(Locale.ROOT);
            BigDecimal[] v = new BigDecimal[11];
            LocalDate deadline = null;
            try {
                if (code == null) throw new IllegalArgumentException("Matricola (DIPENDENTE) mancante");
                if (rate.isEmpty()) throw new IllegalArgumentException("Codice rateo mancante");
                if (!unit.equals("G") && !unit.equals("H")) throw new IllegalArgumentException("Tipo rateo sconosciuto: \"" + unit + "\" (atteso G o H)");
                int m = (int) num(cell(row, col.get("MESE"))).doubleValue(), y = (int) num(cell(row, col.get("ANNO"))).doubleValue();
                ym = YearMonth.of(y, m);
                String[] names = {"RATEO ANNUALE", "ESERCIZIO PREC RESIDUO", "ESERCIZIO PREC GODUTO", "ESERCIZIO PREC SALDO",
                    "ESERCIZIO IN CORSO MATURATO", "ESERCIZIO IN CORSO GODUTO", "ESERCIZIO IN CORSO SALDO", "TOTALE RESIDUO",
                    "SPETTANTE MATURABILE", "TOTALE RATEO", "RESIDUO NON INDENNIZZABILE"};
                for (int i = 0; i < names.length; i++) {
                    Integer c = col.get(names[i]);
                    String s = c == null ? "" : cell(row, c);
                    v[i] = s.isEmpty() ? null : num(s);
                    if (v[i] != null && v[i].abs().compareTo(new BigDecimal("9999")) > 0) throw new IllegalArgumentException("Valore fuori scala in " + names[i]);
                }
                if (v[7] == null) throw new IllegalArgumentException("TOTALE RESIDUO mancante");
                Integer cd = col.get("MM AA DA ASSOGGETTARE");
                String d = cd == null ? "" : cell(row, cd);
                if (!d.isEmpty() && num(d).signum() > 0) deadline = EXCEL_EPOCH.plusDays(num(d).longValue());
            } catch (IllegalArgumentException | java.time.DateTimeException e) {
                err = e instanceof java.time.DateTimeException ? "MESE/ANNO non validi" : e.getMessage();
            }
            out.add(new Line(r + 1, code, cell(row, col.getOrDefault("COGNOME", -1)), cell(row, col.getOrDefault("NOME", -1)), ym, rate,
                cell(row, col.getOrDefault("DESCRIZIONE RATEO", -1)), unit, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], deadline, err));
            if (out.size() > 4000) throw ApiException.bad("Troppe righe nel file");
        }
        return out;
    }

    private static String cell(List<String> row, Integer c) {
        return c != null && c >= 0 && c < row.size() && row.get(c) != null ? row.get(c).trim() : "";
    }

    private static BigDecimal num(String s) {
        String n = s.replace(" ", "");
        if (n.contains(",") && n.contains(".")) n = n.replace(".", "");
        n = n.replace(",", ".");
        try { return new BigDecimal(n); } catch (NumberFormatException e) { throw new IllegalArgumentException("Valore non numerico: \"" + s + "\""); }
    }
}
