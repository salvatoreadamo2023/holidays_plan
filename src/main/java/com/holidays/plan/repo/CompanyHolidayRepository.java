package com.holidays.plan.repo;

import com.holidays.plan.domain.CompanyHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CompanyHolidayRepository extends JpaRepository<CompanyHoliday, Long> {
    List<CompanyHoliday> findByDateBetweenOrderByDate(LocalDate from, LocalDate to);

    boolean existsByDate(LocalDate date);
}
