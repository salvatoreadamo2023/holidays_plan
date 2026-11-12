package com.holidays.plan;

import com.holidays.plan.repo.CompanyHolidayRepository;
import com.holidays.plan.service.HolidayCalendar;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HolidayCalendarTest {
    private final CompanyHolidayRepository repo = Mockito.mock(CompanyHolidayRepository.class);
    private final HolidayCalendar cal;

    HolidayCalendarTest() {
        Mockito.when(repo.findByDateBetweenOrderByDate(Mockito.any(), Mockito.any())).thenReturn(List.of());
        cal = new HolidayCalendar(repo);
    }

    @Test
    void pasquettaEFestivitaFisse() {
        var h = cal.holidays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(h).containsKey(LocalDate.of(2026, 4, 6)); // Pasqua 2026 = 5 aprile
        assertThat(h).containsKey(LocalDate.of(2026, 12, 8));
        assertThat(h).hasSize(11);
    }

    @Test
    void giorniLavorativiEscludonoWeekendEFestivi() {
        // 21-31 dicembre 2026: 25 (ven) e 26 (sab) festivi, weekend esclusi -> 21,22,23,24,28,29,30,31 = 8
        assertThat(cal.workingDays(LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 31))).isEqualTo(BigDecimal.valueOf(8));
    }
}
