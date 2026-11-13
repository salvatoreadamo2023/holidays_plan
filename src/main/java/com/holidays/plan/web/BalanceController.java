package com.holidays.plan.web;

import com.holidays.plan.security.CurrentUser;
import com.holidays.plan.service.BalanceImportService;
import com.holidays.plan.service.BalanceImportService.*;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class BalanceController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final BalanceImportService service;
    private final CurrentUser currentUser;

    public BalanceController(BalanceImportService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** Saldo residuo ufficiale di chi e' collegato (solo informativo). */
    @GetMapping("/api/balances/me")
    public MyBalance mine() {
        return service.mine(currentUser.get());
    }

    @GetMapping("/api/admin/balances")
    @PreAuthorize("hasRole('ADMIN')")
    public List<BalanceRow> list() {
        currentUser.get();
        return service.list();
    }

    @GetMapping("/api/admin/balances/template")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> template() {
        currentUser.get();
        return ResponseEntity.ok().contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("saldi-residui.xlsx").build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(service.template());
    }

    /** apply=false: anteprima; apply=true: salva le righe valide. */
    @PostMapping(path = "/api/admin/balances/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ImportResult importFile(@RequestPart("file") MultipartFile file, @RequestParam String month,
                                   @RequestParam(defaultValue = "false") boolean apply) {
        return service.importFile(currentUser.get(), file, month, apply);
    }
}
