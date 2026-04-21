package com.holidays.plan.model;



import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "holiday_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HolidayRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate dataInizio;

    @Column(nullable = false)
    private LocalDate dataFine;

    @Enumerated(EnumType.STRING)
    private RequestType tipo; // FERIE o PERMESSO

    @Enumerated(EnumType.STRING)
    private RequestStatus stato = RequestStatus.PENDING; // Di default è PENDING

    private String note;

    // Chi ha chiesto le ferie
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}