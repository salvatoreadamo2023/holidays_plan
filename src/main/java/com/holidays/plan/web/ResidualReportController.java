package com.holidays.plan.web;

import com.holidays.plan.security.CurrentUser;
import com.holidays.plan.service.ResidualReportService;
import com.holidays.plan.service.ResidualReportService.Report;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/** Riepilogo rimanenze ipotetiche (cedolino + calendario): solo admin. */
@RestController
public class ResidualReportController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final ResidualReportService service;
    private final CurrentUser currentUser;

    public ResidualReportController(ResidualReportService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/admin/residuals")
    @PreAuthorize("hasRole('ADMIN')")
    public Report report(@RequestParam String from, @RequestParam String to) {
        currentUser.get();
        YearMonth[] p = ResidualReportService.parsePeriod(from, to);
        return service.report(p[0], p[1]);
    }

    @GetMapping("/api/admin/residuals/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> export(@RequestParam String from, @RequestParam String to) {
        currentUser.get();
        YearMonth[] p = ResidualReportService.parsePeriod(from, to);
        return ResponseEntity.ok().contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("residuo-pianificato_" + p[0] + "_" + p[1] + ".xlsx").build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(service.export(p[0], p[1]));
    }
}
