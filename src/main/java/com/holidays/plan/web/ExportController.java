package com.holidays.plan.web;

import com.holidays.plan.domain.User;
import com.holidays.plan.repo.UserRepository;
import com.holidays.plan.security.CurrentUser;
import com.holidays.plan.service.ApiException;
import com.holidays.plan.service.ExportService;
import com.holidays.plan.service.LinkSigner;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Map;

@RestController
public class ExportController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final ExportService export;
    private final CurrentUser currentUser;
    private final LinkSigner signer;
    private final UserRepository users;

    public ExportController(ExportService export, CurrentUser currentUser, LinkSigner signer, UserRepository users) {
        this.export = export;
        this.currentUser = currentUser;
        this.signer = signer;
        this.users = users;
    }

    private static YearMonth ym(int year, int month) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) throw ApiException.bad("Mese non valido");
        return YearMonth.of(year, month);
    }

    private static ResponseEntity<byte[]> file(YearMonth ym, byte[] data) {
        return ResponseEntity.ok().contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(ExportService.fileName(ym)).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(data);
    }

    /** Download diretto (sito web, con token). */
    @GetMapping("/api/export/month")
    public ResponseEntity<byte[]> month(@RequestParam int year, @RequestParam int month) {
        YearMonth ym = ym(year, month);
        return file(ym, export.month(currentUser.get(), ym));
    }

    /** Link firmato di 5 minuti, per aprire il file dal telefono. */
    @GetMapping("/api/export/month/link")
    public Map<String, Object> link(@RequestParam int year, @RequestParam int month) {
        User u = currentUser.get();
        export.checkCanExport(u);
        YearMonth ym = ym(year, month);
        long exp = signer.expiry();
        String sig = signer.sign("export:" + u.getId() + ":" + ym, exp);
        return Map.of("url", "/api/files/export?year=" + year + "&month=" + month + "&uid=" + u.getId() + "&exp=" + exp + "&sig=" + sig,
            "expiresAt", Instant.ofEpochSecond(exp));
    }

    @GetMapping("/api/files/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> signed(@RequestParam int year, @RequestParam int month, @RequestParam long uid,
                                         @RequestParam long exp, @RequestParam String sig) {
        YearMonth ym = ym(year, month);
        signer.verify("export:" + uid + ":" + ym, exp, sig);
        User u = users.findById(uid).filter(User::isActive).orElseThrow(ApiException::notFound);
        return file(ym, export.month(u, ym));
    }
}
