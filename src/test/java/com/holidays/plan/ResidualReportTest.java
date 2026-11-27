package com.holidays.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidays.plan.config.DemoDataSeeder;
import com.holidays.plan.domain.*;
import com.holidays.plan.repo.AbsenceRequestRepository;
import com.holidays.plan.repo.TeamRepository;
import com.holidays.plan.repo.UserRepository;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Riepilogo rimanenze: ricostruisce la riga di un collega dal calendario "IT_Holidays Plan FY26" (ott 2025 - set 2026)
 * con il report ratei del cedolino di febbraio 2026 (.xls) e controlla che i totali siano quelli del riepilogo aziendale.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ResidualReportTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired AbsenceRequestRepository requests;

    private String token(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", DemoDataSeeder.DEMO_PASSWORD))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    private void add(User u, AbsenceType type, String from, String to, RequestStatus st) {
        AbsenceRequest r = new AbsenceRequest();
        r.setUser(u);
        r.setType(type);
        r.setStatus(st);
        r.setStartDate(LocalDate.parse(from));
        r.setEndDate(LocalDate.parse(to));
        r.setAmount(BigDecimal.ONE);
        requests.save(r);
    }

    private void permit(User u, String day, int hours) {
        AbsenceRequest r = new AbsenceRequest();
        r.setUser(u);
        r.setType(AbsenceType.PERMESSO);
        r.setStatus(RequestStatus.APPROVED);
        r.setStartDate(LocalDate.parse(day));
        r.setEndDate(LocalDate.parse(day));
        r.setHours(BigDecimal.valueOf(hours));
        r.setAmount(BigDecimal.valueOf(hours));
        requests.save(r);
    }

    private User person(String username, String fullName) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@example.test");
        u.setFullName(fullName);
        u.setPasswordHash("x");
        u.setRole(Role.USER);
        Team t = teams.findAll().get(0);
        u.setTeam(t);
        u.setArea(t.getArea());
        return users.save(u);
    }

    private static final String[] HEAD = {"AZIENDA", "DENOMINAZIONE", "DIPENDENTE", "COGNOME", "NOME", "MESE", "ANNO", "DATA ASSUNZIONE",
        "DATA CESSAZIONE", "CODICE RATEO", "DESCRIZIONE RATEO", "TIPO RATEO", "RATEO ANNUALE", "MESE CHIUSURA MATURAZIONE",
        "ESERCIZIO PREC. - RESIDUO", "ESERCIZIO PREC. - GODUTO", "ESERCIZIO PREC. - SALDO", "ESERCIZIO IN CORSO - MATURATO",
        "ESERCIZIO IN CORSO - GODUTO", "ESERCIZIO IN CORSO - SALDO", "TOTALE RESIDUO", "SPETTANTE MATURABILE", "TOTALE RATEO",
        "RESIDUO NON INDENNIZZABILE", "FERIE DA GODERE NELL'ANNO", "MM/AA DA ASSOGGETTARE", "Q.TA' DA ASSOGGETTARE"};

    private static void line(Sheet sh, int r, Object... v) {
        Row row = sh.createRow(r);
        for (int i = 0; i < v.length; i++) {
            if (v[i] instanceof Number n) row.createCell(i).setCellValue(n.doubleValue());
            else if (v[i] != null) row.createCell(i).setCellValue(v[i].toString());
        }
    }

    /** Report ratei come lo da' chi elabora le paghe: Adamo (contratto nuovo, 2 voci) e Minardi (3 voci). */
    private static byte[] cedolinoFebbraio() throws Exception {
        try (HSSFWorkbook wb = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("STCGI245_Vacation accrual repor");
            line(sh, 0, (Object[]) HEAD);
            //            az  den    dip   cognome     nome         mese anno ass  ces  cod    desc         tipo annuo chius  prRes     prGod prSaldo   maturato  god  saldo     totRes    maturab   totRateo  nonInd  daGod mm/aa qta
            line(sh, 1, 18, "CGI", 108, "ADAMO", "SALVATORE", 2, 2026, 45062, "", "F01", "Ferie", "G", 22, 12, 29.66666, 6, 23.66666, 3.66666, 0, 3.66666, 27.33332, 18.33334, 45.66666, 23.66666, 0, 46539, 0);
            line(sh, 2, 18, "CGI", 108, "ADAMO", "SALVATORE", 2, 2026, 45062, "", "F06", "ExFs-Rol", "H", 68, 12, 55.33333, 0, 55.33333, 11.33333, 0, 11.33333, 66.66666, 56.66667, 123.33333, 0, 0, "", 0);
            line(sh, 3, 18, "CGI", 80, "MINARDI", "SIMONE", 2, 2026, 43626, "", "F01", "Ferie", "G", 22, 12, 24.46, 4, 20.46, 3.66666, 0, 3.66666, 24.12666, 18.33334, 42.46, 20.46, 0, 46539, 0);
            line(sh, 4, 18, "CGI", 80, "MINARDI", "SIMONE", 2, 2026, 43626, "", "F02", "Perm.Ex-Fs", "H", 32, 12, 32, 0, 32, 5.33333, 0, 5.33333, 37.33333, 26.66667, 64, 0, 0, "", 0);
            line(sh, 5, 18, "CGI", 80, "MINARDI", "SIMONE", 2, 2026, 43626, "", "F03", "Rol/Par", "H", 72, 12, 64, 0, 64, 12, 0, 12, 76, 60, 136, 0, 0, "", 0);
            line(sh, 6, 18, "CGI", 999, "SCONOSCIUTO", "MARIO", 2, 2026, 43626, "", "F01", "Ferie", "G", 22, 12, 1, 0, 1, 1, 0, 1, 2, 1, 3, 0, 0, "", 0);
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void rigaDiAdamo_comeNelRiepilogoAziendale() throws Exception {
        String admin = token("admin");
        User adamo = person("s.adamo", "Salvatore Adamo");
        User minardi = person("s.minardi", "Simone Minardi");
        // Calendario IT_Holidays Plan FY26 (ott 2025 - set 2026), riga SALVATORE ADAMO
        add(adamo, AbsenceType.FERIE, "2025-12-01", "2025-12-05", RequestStatus.APPROVED);
        add(adamo, AbsenceType.FERIE, "2025-12-24", "2025-12-24", RequestStatus.APPROVED);
        add(adamo, AbsenceType.TENTATIVE, "2026-03-02", "2026-03-06", RequestStatus.APPROVED);
        add(adamo, AbsenceType.FERIE, "2026-03-25", "2026-03-26", RequestStatus.APPROVED);
        add(adamo, AbsenceType.FERIE, "2026-04-07", "2026-04-10", RequestStatus.APPROVED);
        add(adamo, AbsenceType.FERIE, "2026-05-04", "2026-05-04", RequestStatus.PENDING);
        add(adamo, AbsenceType.FERIE, "2026-06-23", "2026-06-26", RequestStatus.APPROVED);
        add(adamo, AbsenceType.BUSINESS_TRIP, "2026-07-01", "2026-07-03", RequestStatus.APPROVED);
        add(adamo, AbsenceType.FERIE, "2026-08-17", "2026-08-28", RequestStatus.APPROVED);
        add(adamo, AbsenceType.GENERAL_LEAVE, "2026-09-25", "2026-10-09", RequestStatus.APPROVED);
        add(adamo, AbsenceType.FERIE, "2026-01-12", "2026-01-12", RequestStatus.REJECTED); // non conta
        // riga SIMONE MINARDI (solo da febbraio, con un permesso da 4h)
        add(minardi, AbsenceType.FERIE, "2026-02-23", "2026-02-24", RequestStatus.APPROVED);
        add(minardi, AbsenceType.CLIENT_OFFICE, "2026-03-31", "2026-03-31", RequestStatus.APPROVED);
        add(minardi, AbsenceType.FERIE, "2026-04-03", "2026-04-03", RequestStatus.APPROVED);
        permit(minardi, "2026-06-25", 4);
        add(minardi, AbsenceType.FERIE, "2026-06-26", "2026-06-26", RequestStatus.APPROVED);
        add(minardi, AbsenceType.FERIE, "2026-07-30", "2026-08-14", RequestStatus.APPROVED);
        add(minardi, AbsenceType.FERIE, "2026-09-24", "2026-09-25", RequestStatus.APPROVED);

        MockMultipartFile file = new MockMultipartFile("file", "EP2_STCGI245_Vacation accrual report qty.xls", "application/vnd.ms-excel", cedolinoFebbraio());
        JsonNode prev = json.readTree(mvc.perform(multipart("/api/admin/balances/import").file(file).header("Authorization", admin))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(prev.get("format").asText()).isEqualTo("PAYROLL");
        assertThat(prev.get("referenceMonth").asText()).isEqualTo("2026-02");
        assertThat(prev.get("valid").asInt()).isEqualTo(2);
        assertThat(prev.get("errors").asInt()).isEqualTo(1);
        mvc.perform(multipart("/api/admin/balances/import").file(file).param("apply", "true").header("Authorization", admin))
            .andExpect(status().isOk());
        // riconosciuti dal nome: la matricola resta salvata
        assertThat(users.findById(adamo.getId()).orElseThrow().getEmployeeCode()).isEqualTo("108");

        JsonNode rep = json.readTree(mvc.perform(get("/api/admin/residuals").param("from", "2025-10").param("to", "2026-09")
            .header("Authorization", admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode a = null, m = null;
        for (JsonNode r : rep.get("rows")) {
            if (r.get("userId").asLong() == adamo.getId()) a = r;
            if (r.get("userId").asLong() == minardi.getId()) m = r;
        }
        assertThat(a).isNotNull();
        // Ced. Feb: si conta dal 1° febbraio, le ferie di dicembre sono gia' nel cedolino
        assertThat(a.get("countFrom").asText()).isEqualTo("2026-02-01");
        assertThat(a.get("holidayDays").decimalValue()).isEqualByComparingTo("21");
        assertThat(a.get("tentativeDays").decimalValue()).isEqualByComparingTo("5");
        assertThat(a.get("clientOfficeDays").decimalValue()).isEqualByComparingTo("0");
        assertThat(a.get("permitDays").decimalValue()).isEqualByComparingTo("0");
        assertThat(a.get("grandTotal").decimalValue()).isEqualByComparingTo("26");
        assertThat(a.get("prevLeaveDays").decimalValue()).isEqualByComparingTo("23.67");
        assertThat(a.get("prevPermitDays").decimalValue()).isEqualByComparingTo("6.92");
        assertThat(a.get("prevTotalDays").decimalValue()).isEqualByComparingTo("30.58");
        assertThat(a.get("curLeaveDays").decimalValue()).isEqualByComparingTo("3.67");
        assertThat(a.get("apToPlanDays").decimalValue()).isEqualByComparingTo("4.58");
        assertThat(a.get("apCovered").asBoolean()).isFalse();
        assertThat(a.get("apDeadline").asText()).isEqualTo("2027-06-01");

        assertThat(m.get("holidayDays").decimalValue()).isEqualByComparingTo("18");
        assertThat(m.get("clientOfficeDays").decimalValue()).isEqualByComparingTo("1");
        assertThat(m.get("permit4hHours").decimalValue()).isEqualByComparingTo("4");
        assertThat(m.get("permitDays").decimalValue()).isEqualByComparingTo("0.5");
        assertThat(m.get("grandTotal").decimalValue()).isEqualByComparingTo("18.5");
        assertThat(m.get("prevPermitHours").decimalValue()).isEqualByComparingTo("96");
        assertThat(m.get("prevTotalDays").decimalValue()).isEqualByComparingTo("32.46");
        assertThat(m.get("apToPlanDays").decimalValue()).isEqualByComparingTo("13.96");

        byte[] xlsx = mvc.perform(get("/api/admin/residuals/export").param("from", "2025-10").param("to", "2026-09").header("Authorization", admin))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(xlsx, 0, 2)).isEqualTo("PK");
        java.nio.file.Files.write(java.nio.file.Path.of("target", "riepilogo-test.xlsx"), xlsx);

        // solo admin
        mvc.perform(get("/api/admin/residuals").param("from", "2025-10").param("to", "2026-09").header("Authorization", token("pm")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/residuals").param("from", "2026-09").param("to", "2025-10").header("Authorization", admin))
            .andExpect(status().isBadRequest());
    }
}
