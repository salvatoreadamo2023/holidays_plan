package com.holidays.plan.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Una voce (rateo) dell'ultimo report ratei del cedolino per una persona: Ferie (unita' G, giorni) oppure
 * un tipo di permesso (unita' H, ore: Perm.Ex-Fs, Rol/Par, ExFs-Rol...). Valori reali, come li da' chi elabora le paghe.
 */
@Entity
@Table(name = "payroll_items")
@IdClass(PayrollItem.Key.class)
@Getter
@Setter
public class PayrollItem {
    @Id
    @Column(name = "user_id")
    private Long userId;

    /** CODICE RATEO, es. F01. */
    @Id
    @Column(length = 20)
    private String code;

    /** DESCRIZIONE RATEO, es. Ferie, Perm.Ex-Fs, Rol/Par. */
    @Column(length = 100)
    private String description;

    /** G = giorni, H = ore. */
    @Column(nullable = false, length = 1)
    private String unit;

    /** Primo giorno del mese del cedolino (MESE/ANNO). */
    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "annual_qty", precision = 9, scale = 5) private BigDecimal annualQty;
    @Column(name = "prev_residual", precision = 9, scale = 5) private BigDecimal prevResidual;
    @Column(name = "prev_used", precision = 9, scale = 5) private BigDecimal prevUsed;
    @Column(name = "prev_balance", precision = 9, scale = 5) private BigDecimal prevBalance;
    @Column(name = "cur_accrued", precision = 9, scale = 5) private BigDecimal curAccrued;
    @Column(name = "cur_used", precision = 9, scale = 5) private BigDecimal curUsed;
    @Column(name = "cur_balance", precision = 9, scale = 5) private BigDecimal curBalance;
    /** TOTALE RESIDUO = saldo AP + saldo dell'anno a fine mese del cedolino. */
    @Column(name = "total_residual", precision = 9, scale = 5) private BigDecimal totalResidual;
    /** SPETTANTE MATURABILE: quanto matura ancora fino a fine anno. */
    @Column(name = "still_accruable", precision = 9, scale = 5) private BigDecimal stillAccruable;
    /** TOTALE RATEO = residuo + maturabile. */
    @Column(name = "total_projected", precision = 9, scale = 5) private BigDecimal totalProjected;
    /** RESIDUO NON INDENNIZZABILE: AP da smaltire entro la scadenza. */
    @Column(name = "non_compensable", precision = 9, scale = 5) private BigDecimal nonCompensable;
    /** MM/AA DA ASSOGGETTARE: scadenza oltre la quale l'AP non goduto viene assoggettato. */
    private LocalDate deadline;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public boolean isHours() { return "H".equals(unit); }

    public static class Key implements Serializable {
        private Long userId;
        private String code;

        public Key() {}

        public Key(Long userId, String code) { this.userId = userId; this.code = code; }

        @Override public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(code, k.code);
        }

        @Override public int hashCode() { return Objects.hash(userId, code); }
    }
}
