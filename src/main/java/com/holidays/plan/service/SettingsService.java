package com.holidays.plan.service;

import com.holidays.plan.domain.AppSetting;
import com.holidays.plan.domain.User;
import com.holidays.plan.repo.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Impostazioni generali decise dall'admin: ore di permesso consentite, link APK, email di notifica. */
@Service
public class SettingsService {
    static final String PERMIT_HOURS = "permit.hours", APK_URL = "apk.url", EMAIL_NOTIFY = "notify.email",
        PAYROLL_NEXT_MONTH = "payroll.count.next.month";
    /** Valori selezionabili: da mezz'ora a 8 ore, a passi di mezz'ora. */
    public static final List<BigDecimal> ALL_HOURS;
    static {
        List<BigDecimal> l = new ArrayList<>();
        for (int i = 1; i <= 16; i++) l.add(BigDecimal.valueOf(i * 5L, 1).stripTrailingZeros());
        ALL_HOURS = List.copyOf(l);
    }

    /** Predefinito aziendale: permessi da 2 o 4 ore. */
    public static final List<BigDecimal> DEFAULT_HOURS = List.of(new BigDecimal("2"), new BigDecimal("4"));

    private final AppSettingRepository repo;
    private final AuditService audit;
    private final Clock clock;

    public SettingsService(AppSettingRepository repo, AuditService audit, Clock clock) {
        this.repo = repo;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * payrollCountFromNextMonth: false (predefinito) = nel riepilogo rimanenze le assenze si contano dal 1° del mese del cedolino,
     * perche' il cedolino arriva in ritardo e quel mese non l'ha ancora scalato; true = dal 1° del mese successivo.
     */
    public record Settings(List<BigDecimal> permitHours, String apkUrl, boolean emailNotifications, boolean payrollCountFromNextMonth) {}

    public record SettingsInput(List<BigDecimal> permitHours, String apkUrl, Boolean emailNotifications, Boolean payrollCountFromNextMonth) {}

    @Transactional(readOnly = true)
    public Settings get() {
        Map<String, String> m = new HashMap<>();
        repo.findAll().forEach(s -> m.put(s.getKey(), s.getValue()));
        return new Settings(parseHours(m.get(PERMIT_HOURS)), blankToNull(m.get(APK_URL)), "true".equals(m.get(EMAIL_NOTIFY)),
            "true".equals(m.get(PAYROLL_NEXT_MONTH)));
    }

    public List<BigDecimal> permitHours() { return get().permitHours(); }

    public boolean emailNotifications() { return get().emailNotifications(); }

    public boolean payrollCountFromNextMonth() { return get().payrollCountFromNextMonth(); }

    @Transactional
    public Settings update(User actor, SettingsInput in) {
        Settings before = get();
        List<BigDecimal> hours = in.permitHours() == null ? before.permitHours() : normalize(in.permitHours());
        if (hours.isEmpty()) throw ApiException.bad("Lascia almeno una durata di permesso");
        String apk = in.apkUrl() == null ? before.apkUrl() : blankToNull(in.apkUrl().trim());
        if (apk != null) {
            if (apk.length() > 1000) throw ApiException.bad("Link troppo lungo");
            try {
                URI u = URI.create(apk);
                if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) {
                throw ApiException.bad("Il link dell'APK deve iniziare con https://");
            }
        }
        boolean email = in.emailNotifications() == null ? before.emailNotifications() : in.emailNotifications();
        put(actor, PERMIT_HOURS, String.join(",", hours.stream().map(BigDecimal::toPlainString).toList()));
        put(actor, APK_URL, apk);
        put(actor, EMAIL_NOTIFY, String.valueOf(email));
        boolean next = in.payrollCountFromNextMonth() == null ? before.payrollCountFromNextMonth() : in.payrollCountFromNextMonth();
        put(actor, PAYROLL_NEXT_MONTH, String.valueOf(next));
        audit.log(actor.getId(), null, "SETTINGS_UPDATED", "Ore permesso: " + String.join(", ", hours.stream().map(BigDecimal::toPlainString).toList())
            + " · APK: " + (apk == null ? "nessuno" : "impostato") + " · Email notifiche: " + (email ? "si" : "no")
            + " · Rimanenze: conteggio dal " + (next ? "mese dopo il cedolino" : "mese del cedolino"));
        return get();
    }

    private void put(User actor, String key, String value) {
        AppSetting s = repo.findById(key).orElseGet(AppSetting::new);
        s.setKey(key);
        s.setValue(value);
        s.setUpdatedAt(Instant.now(clock));
        s.setUpdatedBy(actor.getId());
        repo.save(s);
    }

    private static List<BigDecimal> normalize(List<BigDecimal> in) {
        TreeSet<BigDecimal> set = new TreeSet<>();
        for (BigDecimal b : in) {
            if (b == null) continue;
            BigDecimal v = b.stripTrailingZeros();
            if (ALL_HOURS.stream().noneMatch(x -> x.compareTo(v) == 0)) throw ApiException.bad("Durata di permesso non valida: " + b.toPlainString());
            set.add(v);
        }
        return List.copyOf(set);
    }

    static List<BigDecimal> parseHours(String csv) {
        if (csv == null || csv.isBlank()) return DEFAULT_HOURS;
        List<BigDecimal> out = new ArrayList<>();
        for (String p : csv.split(",")) {
            try { out.add(new BigDecimal(p.trim()).stripTrailingZeros()); } catch (NumberFormatException ignored) { /* ignora */ }
        }
        return out.isEmpty() ? DEFAULT_HOURS : out;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
