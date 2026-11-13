package com.holidays.plan.web;

import com.holidays.plan.config.ClockConfig;
import com.holidays.plan.domain.LeaveReason;
import com.holidays.plan.service.ApiException;
import com.holidays.plan.service.StatsService;
import com.holidays.plan.web.dto.StatsView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Dashboard statistiche: solo Admin (protetto da /api/admin/**). */
@RestController
public class StatsController {
    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/api/admin/stats")
    public StatsView stats(@RequestParam(required = false) Integer year, @RequestParam(required = false) LeaveReason reason) {
        int y = year == null ? LocalDate.now(ClockConfig.ZONE).getYear() : year;
        if (y < 2000 || y > 2100) throw ApiException.bad("Anno non valido");
        return stats.stats(y, reason);
    }
}
