package com.holidays.plan.web;

import com.holidays.plan.security.CurrentUser;
import com.holidays.plan.service.SettingsService;
import com.holidays.plan.service.SettingsService.Settings;
import com.holidays.plan.service.SettingsService.SettingsInput;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class SettingsController {
    private final SettingsService settings;
    private final CurrentUser currentUser;

    public SettingsController(SettingsService settings, CurrentUser currentUser) {
        this.settings = settings;
        this.currentUser = currentUser;
    }

    /** Per tutti gli utenti collegati: ore di permesso consentite e link dell'APK. */
    public record PublicSettings(List<BigDecimal> permitHours, String apkUrl) {}

    @GetMapping("/api/settings")
    public PublicSettings get() {
        currentUser.get();
        Settings s = settings.get();
        return new PublicSettings(s.permitHours(), s.apkUrl());
    }

    @GetMapping("/api/admin/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public Settings adminGet() {
        currentUser.get();
        return settings.get();
    }

    @PutMapping("/api/admin/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public Settings update(@RequestBody SettingsInput in) {
        return settings.update(currentUser.get(), in);
    }
}
