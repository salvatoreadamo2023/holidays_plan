package com.holidays.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidays.plan.config.DemoDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityAndFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    /** Ogni test usa una settimana diversa per non sovrapporsi. */
    private static final AtomicInteger WEEK = new AtomicInteger(8);

    private static LocalDate freshMonday() {
        // Salta le settimane con una festivita' (Pasquetta, Natale...): i test vogliono 5 giorni lavorativi pieni.
        while (true) {
            LocalDate m = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(WEEK.getAndIncrement());
            boolean free = true;
            for (int i = 0; i < 5; i++) {
                LocalDate x = m.plusDays(i);
                if (x.equals(com.holidays.plan.service.HolidayCalendar.easter(x.getYear()).plusDays(1))
                    || java.util.Set.of("01-01", "01-06", "04-25", "05-01", "06-02", "08-15", "11-01", "12-08", "12-25", "12-26")
                        .contains(String.format("%02d-%02d", x.getMonthValue(), x.getDayOfMonth()))) free = false;
            }
            if (free) return m;
        }
    }

    private String token(String username) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", DemoDataSeeder.DEMO_PASSWORD))))
            .andExpect(status().isOk()).andReturn();
        return "Bearer " + json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private long createFerie(String tok, LocalDate from, LocalDate to) throws Exception {
        MvcResult r = mvc.perform(post("/api/requests").header("Authorization", tok).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "FERIE", "startDate", from.toString(), "endDate", to.toString()))))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void apiRichiedeLogin() throws Exception {
        mvc.perform(get("/api/requests/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void credenzialiErrateEBloccoDopoTentativi() throws Exception {
        String body = json.writeValueAsString(Map.of("username", "elena", "password", "sbagliata"));
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void userNonAccedeAdAdmin() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", token("user"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", token("pm"))).andExpect(status().isForbidden());
    }

    @Test
    void nessunaPasswordNelleRisposte() throws Exception {
        String body = mvc.perform(get("/api/admin/users").header("Authorization", token("admin")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body.toLowerCase()).doesNotContain("\"password\"").doesNotContain("passwordhash").doesNotContain("$2a$");
    }

    @Test
    void utenteDalBodyIgnorato_richiestaSempreDellUtenteLoggato() throws Exception {
        LocalDate d = freshMonday();
        MvcResult r = mvc.perform(post("/api/requests").header("Authorization", token("user")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"FERIE\",\"startDate\":\"" + d + "\",\"endDate\":\"" + d + "\",\"user\":{\"id\":1},\"id\":1,\"status\":\"APPROVED\"}"))
            .andExpect(status().isCreated()).andReturn();
        JsonNode n = json.readTree(r.getResponse().getContentAsString());
        assertThat(n.get("user").get("username").asText()).isEqualTo("user");
        assertThat(n.get("status").asText()).isEqualTo("PENDING");
        assertThat(n.get("id").asLong()).isNotEqualTo(1L);
    }

    @Test
    void techLeadDelTeamApprovaUnoUser_altroTeamNo() throws Exception {
        LocalDate d = freshMonday();
        long id = createFerie(token("user"), d, d.plusDays(1));
        // PM di un'altra area: vietato
        mvc.perform(post("/api/requests/" + id + "/approve").header("Authorization", token("pm2")))
            .andExpect(status().isForbidden());
        // L'utente stesso: vietato
        mvc.perform(post("/api/requests/" + id + "/approve").header("Authorization", token("user")))
            .andExpect(status().isForbidden());
        // TL di un altro team (stessa area): vietato, decide solo sul proprio team
        mvc.perform(post("/api/requests/" + id + "/approve").header("Authorization", token("techlead2")))
            .andExpect(status().isForbidden());
        // TL dello stesso team: consentito
        mvc.perform(post("/api/requests/" + id + "/approve").header("Authorization", token("techlead")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        // Gia' decisa: conflitto
        mvc.perform(post("/api/requests/" + id + "/approve").header("Authorization", token("pm")))
            .andExpect(status().isConflict());
    }

    @Test
    void nessunoApprovaSeStesso_eTLNonApprovaIlPM() throws Exception {
        LocalDate d = freshMonday();
        String tl = token("techlead");
        long own = createFerie(tl, d, d);
        mvc.perform(post("/api/requests/" + own + "/approve").header("Authorization", tl)).andExpect(status().isForbidden());
        mvc.perform(post("/api/requests/" + own + "/approve").header("Authorization", token("techlead2"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/requests/" + own + "/approve").header("Authorization", token("pm"))).andExpect(status().isOk());

        long pmReq = createFerie(token("pm"), d.plusDays(2), d.plusDays(2));
        mvc.perform(post("/api/requests/" + pmReq + "/approve").header("Authorization", tl)).andExpect(status().isForbidden());
        mvc.perform(post("/api/requests/" + pmReq + "/approve").header("Authorization", token("admin"))).andExpect(status().isOk());
    }

    @Test
    void rifiutoRichiedeMotivo() throws Exception {
        LocalDate d = freshMonday();
        long id = createFerie(token("sara"), d, d);
        String pm = token("pm");
        mvc.perform(post("/api/requests/" + id + "/reject").header("Authorization", pm)
            .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/requests/" + id + "/reject").header("Authorization", pm)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Rilascio in quella settimana\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void sovrapposizioneBloccata() throws Exception {
        LocalDate d = freshMonday();
        String t = token("davide");
        createFerie(t, d, d.plusDays(2));
        mvc.perform(post("/api/requests").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "FERIE", "startDate", d.plusDays(1).toString(), "endDate", d.plusDays(3).toString()))))
            .andExpect(status().isConflict());
    }

    @Test
    void annullamentoRipristinaSaldo() throws Exception {
        LocalDate d = freshMonday();
        String t = token("andrea");
        double before = json.readTree(mvc.perform(get("/api/balance?year=" + d.getYear()).header("Authorization", t))
            .andReturn().getResponse().getContentAsString()).get("leaveAvailable").asDouble();
        long id = createFerie(t, d, d.plusDays(1));
        mvc.perform(post("/api/requests/" + id + "/approve").header("Authorization", token("pm2"))).andExpect(status().isOk());
        mvc.perform(post("/api/requests/" + id + "/cancel").header("Authorization", t)).andExpect(status().isOk());
        mvc.perform(post("/api/requests/" + id + "/cancel/accept").header("Authorization", token("pm2")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        double after = json.readTree(mvc.perform(get("/api/balance?year=" + d.getYear()).header("Authorization", t))
            .andReturn().getResponse().getContentAsString()).get("leaveAvailable").asDouble();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void ilSaldoNonBloccaLaRichiesta() throws Exception {
        LocalDate d = LocalDate.now().plusYears(1).withMonth(1).withDayOfMonth(10);
        mvc.perform(post("/api/requests").header("Authorization", token("elena")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "FERIE", "startDate", d.toString(), "endDate", d.plusDays(55).toString()))))
            .andExpect(status().isCreated());
    }

    @Test
    void andamentoContaSoloLeApprovate() throws Exception {
        LocalDate d = freshMonday();
        String t = token("andrea");
        JsonNode before = json.readTree(mvc.perform(get("/api/trend?year=" + d.getYear()).header("Authorization", t))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long id = createFerie(t, d, d.plusDays(1));
        JsonNode pending = json.readTree(mvc.perform(get("/api/trend?year=" + d.getYear()).header("Authorization", t))
            .andReturn().getResponse().getContentAsString());
        assertThat(pending.get("leaveDays").asDouble()).isEqualTo(before.get("leaveDays").asDouble());
        assertThat(pending.get("leavePending").asDouble()).isEqualTo(before.get("leavePending").asDouble() + 2);
        mvc.perform(post("/api/requests/" + id + "/approve").header("Authorization", token("pm2"))).andExpect(status().isOk());
        JsonNode after = json.readTree(mvc.perform(get("/api/trend?year=" + d.getYear()).header("Authorization", t))
            .andReturn().getResponse().getContentAsString());
        assertThat(after.get("leaveDays").asDouble()).isEqualTo(before.get("leaveDays").asDouble() + 2);
        assertThat(after.get("absenceDays").asDouble()).isEqualTo(before.get("absenceDays").asDouble() + 2);
    }

    private static final byte[] PDF = "%PDF-1.4\n%test\n".getBytes();

    @Test
    void allegati_caricaVediScaricaConLinkFirmato() throws Exception {
        LocalDate d = freshMonday();
        String t = token("andrea");
        long id = createFerie(t, d, d);
        String up = mvc.perform(multipart("/api/requests/" + id + "/attachments")
                .file(new MockMultipartFile("file", "../../certificato medico.pdf", "application/pdf", PDF))
                .header("Authorization", t))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode att = json.readTree(up);
        assertThat(att.get("filename").asText()).isEqualTo("certificato medico.pdf");
        long attId = att.get("id").asLong();

        // Compare nelle mie richieste e in quelle da approvare del PM dell'area
        assertThat(mvc.perform(get("/api/requests/mine").header("Authorization", t)).andReturn().getResponse()
            .getContentAsString()).contains("certificato medico.pdf");
        assertThat(mvc.perform(get("/api/requests/to-approve").header("Authorization", token("pm2"))).andReturn()
            .getResponse().getContentAsString()).contains("certificato medico.pdf");

        // Chi non vede i dettagli non lo vede e non ottiene il link
        mvc.perform(get("/api/attachments/" + attId + "/link").header("Authorization", token("elena")))
            .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/requests/" + id + "/attachments")
                .file(new MockMultipartFile("file", "x.pdf", "application/pdf", PDF))
                .header("Authorization", token("pm2")))
            .andExpect(status().isForbidden());

        String url = json.readTree(mvc.perform(get("/api/attachments/" + attId + "/link").header("Authorization", token("pm2")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("url").asText();
        mvc.perform(get(url)).andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf")).andExpect(content().bytes(PDF));
        mvc.perform(get(url.replaceAll("sig=.*", "sig=falsa"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/files/" + attId + "?exp=1&sig=x")).andExpect(status().isGone());

        mvc.perform(delete("/api/attachments/" + attId).header("Authorization", t)).andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void allegati_soloPdfJpgPng() throws Exception {
        LocalDate d = freshMonday();
        String t = token("andrea");
        long id = createFerie(t, d, d);
        mvc.perform(multipart("/api/requests/" + id + "/attachments")
                .file(new MockMultipartFile("file", "finto.pdf", "application/pdf", "<script>alert(1)</script>".getBytes()))
                .header("Authorization", t))
            .andExpect(status().isBadRequest());
    }

    @Test
    void calendarioUser_nascondeTipoEAltreAree() throws Exception {
        LocalDate from = LocalDate.now().withDayOfMonth(1);
        String t = token("user");
        String body = mvc.perform(get("/api/calendar").param("from", from.toString()).param("to", from.plusMonths(2).toString())
                .header("Authorization", t))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode v = json.readTree(body);
        long me = json.readTree(mvc.perform(get("/api/auth/me").header("Authorization", t)).andReturn().getResponse().getContentAsString()).get("id").asLong();
        for (JsonNode e : v.get("entries")) {
            if (e.get("userId").asLong() != me) {
                assertThat(e.get("type").asText()).isEqualTo("ASSENZA");
                assertThat(e.get("status").asText()).isNotEqualTo("PENDING");
            }
        }
        assertThat(body).doesNotContain("MALATTIA");
        // Area Operations (id 2): vietata a uno user di Sviluppo
        mvc.perform(get("/api/calendar").param("from", from.toString()).param("to", from.plusDays(10).toString())
            .param("areaId", "2").header("Authorization", t)).andExpect(status().isForbidden());
    }

    @Test
    void daApprovare_soloCompetenza() throws Exception {
        String body = mvc.perform(get("/api/requests/to-approve").header("Authorization", token("pm2")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Sviluppo");
        mvc.perform(get("/api/requests/to-approve").header("Authorization", token("user")))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void nuovoUtenteDeveCambiarePasswordESaldoAZero() throws Exception {
        String admin = token("admin");
        String body = json.writeValueAsString(Map.of("username", "nuovo.utente", "email", "nuovo@example.com",
            "fullName", "Nuovo Utente", "password", "Iniziale-2026!", "role", "USER", "areaId", 1));
        String created = mvc.perform(post("/api/admin/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(created).get("annualLeaveDays").decimalValue().signum()).isZero();
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "nuovo.utente", "password", "Iniziale-2026!"))))
            .andExpect(status().isOk()).andReturn();
        var login = json.readTree(r.getResponse().getContentAsString());
        assertThat(login.get("user").get("mustChangePassword").asBoolean()).isTrue();
        String tok = "Bearer " + login.get("token").asText();
        mvc.perform(get("/api/requests/mine").header("Authorization", tok)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header("Authorization", tok)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/password").header("Authorization", tok).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", "Iniziale-2026!", "newPassword", "Personale-2026!"))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/requests/mine").header("Authorization", tok)).andExpect(status().isOk());
    }

    @Test
    void exportMensile_adminEManager_userNo() throws Exception {
        LocalDate d = freshMonday();
        String q = "?year=" + d.getYear() + "&month=" + d.getMonthValue();
        byte[] x = mvc.perform(get("/api/export/month" + q).header("Authorization", token("admin")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(x, 0, 2)).isEqualTo("PK");
        mvc.perform(get("/api/export/month" + q).header("Authorization", token("user"))).andExpect(status().isForbidden());
        String url = json.readTree(mvc.perform(get("/api/export/month/link" + q).header("Authorization", token("pm2")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("url").asText();
        mvc.perform(get(url)).andExpect(status().isOk());
        mvc.perform(get(url.replace("uid=", "uid=1"))).andExpect(status().isNotFound());
    }

    @Test
    void storicoSoloAdmin() throws Exception {
        mvc.perform(get("/api/admin/audit").header("Authorization", token("admin"))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/audit").header("Authorization", token("pm"))).andExpect(status().isForbidden());
    }

    @Test
    void registrazioneTelefono() throws Exception {
        String t = token("user");
        mvc.perform(post("/api/devices").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"ExponentPushToken[abcdefghij123456]\",\"platform\":\"android\"}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/devices").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"nonvalido\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/devices/remove").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"ExponentPushToken[abcdefghij123456]\"}")).andExpect(status().isNoContent());
    }

    @Autowired com.holidays.plan.service.CertificateReminder reminder;

    @Test
    void promemoriaCertificato_soloMalattieVecchieSenzaAllegato() throws Exception {
        // Le malattie appena create non vanno sollecitate
        LocalDate d = LocalDate.now().minusDays(3);
        while (d.getDayOfWeek().getValue() > 5) d = d.minusDays(1);
        mvc.perform(post("/api/requests").header("Authorization", token("sara")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "MALATTIA", "startDate", d.toString(), "endDate", d.toString()))))
            .andExpect(status().isCreated());
        assertThat(reminder.run()).isGreaterThanOrEqualTo(0);
        int again = reminder.run();
        assertThat(again).isZero();
    }

    @Test
    void unicoAdmin_richiestaApprovataInAutomatico_eAnnullamentoDiretto() throws Exception {
        // Nei dati demo c'e' un solo Admin: nessuno sopra di lui puo' decidere.
        String admin = token("admin");
        LocalDate d = freshMonday();
        String body = mvc.perform(post("/api/requests").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "FERIE", "startDate", d.toString(), "endDate", d.toString()))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED")).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();
        mvc.perform(post("/api/requests/" + id + "/cancel").header("Authorization", admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        // Un utente normale invece resta in attesa del responsabile
        mvc.perform(post("/api/requests").header("Authorization", token("andrea")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "FERIE", "startDate", freshMonday().toString(), "endDate", freshMonday().minusWeeks(1).plusDays(1).toString()))))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void malattiaRegistrataSubito_senzaApprovazione() throws Exception {
        LocalDate d = freshMonday();
        mvc.perform(post("/api/requests").header("Authorization", token("davide")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "MALATTIA", "startDate", d.toString(), "endDate", d.plusDays(1).toString()))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.decisionNote").value("Malattia registrata"));
    }

    @Test
    void mezzaGiornataNonPiuPrevista() throws Exception {
        LocalDate d = freshMonday();
        mvc.perform(post("/api/requests").header("Authorization", token("user")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "FERIE", "startDate", d.toString(), "endDate", d.toString(), "halfDay", true))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void pmDecideSoloIlProprioTeam_tlNoAltroTeam() throws Exception {
        LocalDate d = freshMonday();
        long id = createFerie(token("davide"), d, d); // team mobile
        mvc.perform(post("/api/requests/" + id + "/approve").header("Authorization", token("techlead")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/requests/" + id + "/approve").header("Authorization", token("techlead2")))
            .andExpect(status().isOk());
    }

    @Test
    void perContoDi_adminEPm_giaApprovata_userNo() throws Exception {
        String admin = token("admin");
        String people = mvc.perform(get("/api/requests/on-behalf/people").header("Authorization", token("pm2")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(people).contains("andrea").doesNotContain("\"username\":\"user\"");
        mvc.perform(get("/api/requests/on-behalf/people").header("Authorization", token("user")))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        long sara = userId(admin, "sara");
        // Data passata ammessa per conto di
        LocalDate past = LocalDate.now().minusDays(20);
        while (past.getDayOfWeek().getValue() > 5) past = past.minusDays(1);
        mvc.perform(post("/api/requests").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "FERIE", "startDate", past.toString(), "endDate", past.toString(), "forUserId", sara))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.user.username").value("sara")).andExpect(jsonPath("$.decidedBy").exists());
        // PM di un'altra area: vietato
        mvc.perform(post("/api/requests").header("Authorization", token("pm2")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "FERIE", "startDate", freshMonday().toString(), "endDate", freshMonday().toString(), "forUserId", sara))))
            .andExpect(status().isForbidden());
        // Un TL non puo' inserire per conto di altri
        mvc.perform(post("/api/requests").header("Authorization", token("techlead")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "FERIE", "startDate", freshMonday().toString(), "endDate", freshMonday().toString(), "forUserId", sara))))
            .andExpect(status().isForbidden());
    }

    private long userId(String admin, String username) throws Exception {
        JsonNode all = json.readTree(mvc.perform(get("/api/admin/users").header("Authorization", admin))
            .andReturn().getResponse().getContentAsString());
        for (JsonNode u : all) if (u.get("username").asText().equals(username)) return u.get("id").asLong();
        throw new IllegalStateException(username);
    }

    @Test
    void chiusuraAziendale_ricalcolaIGiorni() throws Exception {
        LocalDate d = freshMonday();
        long id = createFerie(token("sara"), d, d.plusDays(4));
        String admin = token("admin");
        String h = mvc.perform(post("/api/admin/holidays").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("date", d.plusDays(2).toString(), "name", "Chiusura test"))))
            .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        assertThat(amountOf("sara", id)).isEqualByComparingTo("4");
        mvc.perform(delete("/api/admin/holidays/" + json.readTree(h).get("id").asLong()).header("Authorization", admin))
            .andExpect(status().is2xxSuccessful());
        assertThat(amountOf("sara", id)).isEqualByComparingTo("5");
    }

    private java.math.BigDecimal amountOf(String username, long id) throws Exception {
        for (JsonNode r : json.readTree(mvc.perform(get("/api/requests/mine").header("Authorization", token(username)))
                .andReturn().getResponse().getContentAsString()))
            if (r.get("id").asLong() == id) return r.get("amount").decimalValue();
        throw new IllegalStateException();
    }
}
