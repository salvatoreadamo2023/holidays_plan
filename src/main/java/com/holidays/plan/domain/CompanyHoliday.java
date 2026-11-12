package com.holidays.plan.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** Chiusure aziendali e santo patrono, in aggiunta alle festivita' nazionali calcolate. */
@Entity
@Table(name = "company_holidays")
@Getter
@Setter
public class CompanyHoliday {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate date;

    @Column(nullable = false, length = 100)
    private String name;
}
