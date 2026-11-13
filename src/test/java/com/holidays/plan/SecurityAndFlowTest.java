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
            .andExpect(status().isCreated()); // il PM responsabile puo' allegare (es. assenza inserita per conto)

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
        String admin = token("admin");
        mvc.perform(get("/api/admin/audit").header("Authorization", admin)).andExpect(status().isOk());
        // Paginazione: due pagine da 2 senza sovrapposizioni, in ordine dalla piu' recente
        for (int i = 0; i < 3; i++) createFerie(token("sara"), freshMonday(), freshMonday().minusWeeks(1));
        JsonNode p1 = json.readTree(mvc.perform(get("/api/admin/audit").param("size", "2").header("Authorization", admin))
            .andReturn().getResponse().getContentAsString());
        assertThat(p1.size()).isEqualTo(2);
        long last = p1.get(1).get("id").asLong();
        assertThat(p1.get(0).get("id").asLong()).isGreaterThan(last);
        JsonNode p2 = json.readTree(mvc.perform(get("/api/admin/audit").param("size", "2").param("before", String.valueOf(last))
            .header("Authorization", admin)).andReturn().getResponse().getContentAsString());
        assertThat(p2.size()).isEqualTo(2);
        assertThat(p2.get(0).get("id").asLong()).isLessThan(last);
        // Ricerca per persona
        JsonNode found = json.readTree(mvc.perform(get("/api/admin/audit").param("q", "sara").header("Authorization", admin))
            .andReturn().getResponse().getContentAsString());
        assertThat(found.size()).isGreaterThan(0);
        for (JsonNode e : found) assertThat((e.get("actorName").asText() + " " + e.path("ownerName").asText()).toLowerCase()).contains("sara");
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
                .content(json.writeValueAsString(Map.of("type", "GENERAL_LEAVE", "leaveReason", "MALATTIA", "startDate", d.toString(), "endDate", d.toString()))))
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
    void generalLeaveRegistratoSubito_conTipologia_eVecchioNomeMalattia() throws Exception {
        LocalDate d = freshMonday();
        mvc.perform(post("/api/requests").header("Authorization", token("davide")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "MALATTIA", "leaveReason", "LEGGE_104", "startDate", d.toString(), "endDate", d.plusDays(1).toString()))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.type").value("GENERAL_LEAVE")).andExpect(jsonPath("$.leaveReason").value("LEGGE_104"));
        // Senza tipologia: rifiutata
        mvc.perform(post("/api/requests").header("Authorization", token("davide")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "GENERAL_LEAVE", "startDate", d.plusWeeks(30).toString(), "endDate", d.plusWeeks(30).toString()))))
            .andExpect(status().isBadRequest());
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

    @Test
    void clientOfficeVisibileATutti_senzaApprovazione_eSiTogliDaSolo() throws Exception {
        LocalDate d = freshMonday();
        String body = mvc.perform(post("/api/requests").header("Authorization", token("sara")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "CLIENT_OFFICE", "startDate", d.toString(), "endDate", d.plusDays(2).toString()))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED")).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();
        // Un collega (user, stesso team) vede il tipo CO, non "ASSENZA"
        String cal = mvc.perform(get("/api/calendar").param("from", d.toString()).param("to", d.plusDays(4).toString())
                .header("Authorization", token("user"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(cal).contains("CLIENT_OFFICE");
        // Nessuna approvazione in coda
        assertThat(mvc.perform(get("/api/requests/to-approve").header("Authorization", token("techlead")))
            .andReturn().getResponse().getContentAsString()).doesNotContain("\"id\":" + id + ",");
        // Si toglie senza chiedere
        mvc.perform(post("/api/requests/" + id + "/cancel").header("Authorization", token("sara")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void ferieIpoteticheTH_visibili_senzaApprovazione_eTrasformabiliInRichiesta() throws Exception {
        LocalDate d = freshMonday();
        String body = mvc.perform(post("/api/requests").header("Authorization", token("sara")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "TENTATIVE", "startDate", d.toString(), "endDate", d.plusDays(1).toString()))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED")).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();
        String cal = mvc.perform(get("/api/calendar").param("from", d.toString()).param("to", d.plusDays(4).toString())
                .header("Authorization", token("user"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(cal).contains("TENTATIVE");
        // Due TH sugli stessi giorni no
        mvc.perform(post("/api/requests").header("Authorization", token("sara")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "TENTATIVE", "startDate", d.toString(), "endDate", d.toString()))))
            .andExpect(status().isConflict());
        // Le TH non bloccano una richiesta vera: l'anteprima di ferie negli stessi giorni non da' avvisi
        mvc.perform(post("/api/requests/preview").header("Authorization", token("sara")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "FERIE", "startDate", d.toString(), "endDate", d.toString()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.warnings").isEmpty());
        // Un altro non puo' trasformarla
        mvc.perform(post("/api/requests/" + id + "/convert").header("Authorization", token("user"))).andExpect(status().isForbidden());
        // Trasformata: nasce una richiesta di ferie in attesa con le stesse date, la TH sparisce
        mvc.perform(post("/api/requests/" + id + "/convert").header("Authorization", token("sara")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("FERIE")).andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.startDate").value(d.toString())).andExpect(jsonPath("$.endDate").value(d.plusDays(1).toString()));
        String mine = mvc.perform(get("/api/requests/mine").header("Authorization", token("sara"))).andReturn().getResponse().getContentAsString();
        for (JsonNode r : json.readTree(mine)) if (r.get("id").asLong() == id) assertThat(r.get("status").asText()).isEqualTo("CANCELLED");
        // Non si trasforma due volte
        mvc.perform(post("/api/requests/" + id + "/convert").header("Authorization", token("sara"))).andExpect(status().isConflict());
    }

    private JsonNode stats(String admin, int year) throws Exception {
        return json.readTree(mvc.perform(get("/api/admin/stats").param("year", String.valueOf(year)).header("Authorization", admin))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static java.math.BigDecimal month(JsonNode v, String type, int m) {
        return v.get("types").get(type).get("months").get(m - 1).decimalValue();
    }

    private static java.math.BigDecimal total(JsonNode v, String type) {
        return v.get("types").get(type).get("total").decimalValue();
    }

    private void onBehalf(String admin, long userId, Map<String, Object> body) throws Exception {
        Map<String, Object> b = new java.util.HashMap<>(body);
        b.put("forUserId", userId);
        mvc.perform(post("/api/requests").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(b))).andExpect(status().isCreated());
    }

    @Test
    void statistiche_soloAdmin_eConteggiSpezzatiSuMesiEAnni() throws Exception {
        mvc.perform(get("/api/admin/stats").header("Authorization", token("pm"))).andExpect(status().isForbidden());
        String admin = token("admin");
        // Persona nuova, senza altre assenze: i conteggi si confrontano prima/dopo
        mvc.perform(post("/api/admin/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", "stats.test", "email", "stats@example.com",
                "fullName", "Stats Test", "password", "Iniziale-2026!", "role", "USER", "areaId", 1)))).andExpect(status().isCreated());
        long elena = userId(admin, "stats.test");
        int y = LocalDate.now().getYear();
        JsonNode a0 = stats(admin, y), b0 = stats(admin, y + 1);

        // A cavallo d'anno: 30-31 dic (2 gg) | 1 gen festivo, 2-3 weekend | 4 gen (1 gg)
        onBehalf(admin, elena, Map.of("type", "FERIE", "startDate", y + "-12-30", "endDate", (y + 1) + "-01-04"));
        // A cavallo di mese nell'anno dopo: gio 28 e ven 29 gen | lun 1 e mar 2 feb (date del 2027)
        LocalDate s = LocalDate.of(y + 1, 1, 28);
        onBehalf(admin, elena, Map.of("type", "GENERAL_LEAVE", "leaveReason", "LEGGE_104", "startDate", s.toString(), "endDate", s.plusDays(5).toString()));
        // Permesso parziale di 3 ore e una giornata presso cliente
        onBehalf(admin, elena, Map.of("type", "PERMESSO", "hours", 3, "startDate", (y + 1) + "-02-10", "endDate", (y + 1) + "-02-10"));
        onBehalf(admin, elena, Map.of("type", "CLIENT_OFFICE", "startDate", (y + 1) + "-02-11", "endDate", (y + 1) + "-02-11"));

        JsonNode a1 = stats(admin, y), b1 = stats(admin, y + 1);
        assertThat(month(a1, "FERIE", 12).subtract(month(a0, "FERIE", 12))).isEqualByComparingTo("2");
        assertThat(month(b1, "FERIE", 1).subtract(month(b0, "FERIE", 1))).isEqualByComparingTo("1");
        assertThat(total(a1, "FERIE").subtract(total(a0, "FERIE"))).isEqualByComparingTo("3");
        assertThat(month(b1, "GENERAL_LEAVE", 1).subtract(month(b0, "GENERAL_LEAVE", 1))).isEqualByComparingTo("2");
        assertThat(month(b1, "GENERAL_LEAVE", 2).subtract(month(b0, "GENERAL_LEAVE", 2))).isEqualByComparingTo("2");
        assertThat(month(b1, "PERMESSO", 2).subtract(month(b0, "PERMESSO", 2))).isEqualByComparingTo("3");
        assertThat(month(b1, "CLIENT_OFFICE", 2).subtract(month(b0, "CLIENT_OFFICE", 2))).isEqualByComparingTo("1");
        assertThat(b1.get("years").toString()).contains(String.valueOf(y + 1));
        // Tipologia e team
        boolean found = false;
        for (JsonNode r : b1.get("reasons")) if (r.get("reason").asText().equals("LEGGE_104")) {
            found = true; assertThat(r.get("series").get("months").get(1).decimalValue()).isGreaterThanOrEqualTo(new java.math.BigDecimal("2"));
        }
        assertThat(found).isTrue();
        // Filtro per tipologia: con reason=MALATTIA la Legge 104 non conta
        JsonNode onlySick = json.readTree(mvc.perform(get("/api/admin/stats").param("year", String.valueOf(y + 1)).param("reason", "MALATTIA")
            .header("Authorization", admin)).andReturn().getResponse().getContentAsString());
        assertThat(month(onlySick, "GENERAL_LEAVE", 2)).isLessThan(month(b1, "GENERAL_LEAVE", 2));
        assertThat(b1.get("people").asInt()).isGreaterThan(5);
    }

    @Test
    void saldiResidui_modelloAnteprimaCaricamentoEVistaPersonale() throws Exception {
        String admin = token("admin");
        byte[] tpl = mvc.perform(get("/api/admin/balances/template").header("Authorization", admin))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(tpl, 0, 2)).isEqualTo("PK");
        mvc.perform(get("/api/admin/balances/template").header("Authorization", token("user"))).andExpect(status().isForbidden());

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        org.dhatim.fastexcel.Workbook wb = new org.dhatim.fastexcel.Workbook(out, "test", "1.0");
        org.dhatim.fastexcel.Worksheet ws = wb.newWorksheet("Saldi");
        ws.value(0, 0, "Username"); ws.value(0, 1, "Nome e cognome"); ws.value(0, 3, "Ferie residue (giorni)"); ws.value(0, 4, "Permessi residui (ore)");
        ws.value(1, 0, "user"); ws.value(1, 3, "14,5"); ws.value(1, 4, 32);
        ws.value(2, 0, "nessuno"); ws.value(2, 3, 3);
        ws.value(3, 0, "pm"); ws.value(3, 3, "abc");
        wb.finish();
        MockMultipartFile file = new MockMultipartFile("file", "saldi.xlsx", "application/octet-stream", out.toByteArray());
        String month = java.time.YearMonth.now().minusMonths(1).toString();

        JsonNode prev = json.readTree(mvc.perform(multipart("/api/admin/balances/import").file(file).param("month", month)
                .header("Authorization", admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(prev.get("valid").asInt()).isEqualTo(1);
        assertThat(prev.get("errors").asInt()).isEqualTo(2);
        assertThat(prev.get("applied").asBoolean()).isFalse();
        // anteprima: niente salvato
        assertThat(json.readTree(mvc.perform(get("/api/balances/me").header("Authorization", token("user")))
            .andReturn().getResponse().getContentAsString()).get("available").asBoolean()).isFalse();

        mvc.perform(multipart("/api/admin/balances/import").file(file).param("month", month).param("apply", "true")
            .header("Authorization", admin)).andExpect(status().isOk());
        JsonNode me = json.readTree(mvc.perform(get("/api/balances/me").header("Authorization", token("user")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(me.get("available").asBoolean()).isTrue();
        assertThat(me.get("referenceMonth").asText()).isEqualTo(month);
        assertThat(me.get("leaveDays").decimalValue()).isEqualByComparingTo("14.5");
        assertThat(me.get("permitHours").decimalValue()).isEqualByComparingTo("32");

        mvc.perform(multipart("/api/admin/balances/import").file(file).param("month", month).header("Authorization", token("pm")))
            .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/balances/import").file(file).param("month", "2999-01").header("Authorization", admin))
            .andExpect(status().isBadRequest());
    }

    private static String period(LocalDate from, LocalDate to) {
        return "{\"type\":\"FERIE\",\"startDate\":\"" + from + "\",\"endDate\":\"" + to + "\"}";
    }

    private String statusOf(String tok, long id) throws Exception {
        for (JsonNode n : json.readTree(mvc.perform(get("/api/requests/mine").header("Authorization", tok))
                .andReturn().getResponse().getContentAsString()))
            if (n.get("id").asLong() == id) return n.get("status").asText();
        return null;
    }

    @Test
    void rettifica_chiestaDallUtente_eDirettaDelResponsabile() throws Exception {
        LocalDate d = freshMonday();
        String u = token("user"), tl = token("techlead");
        long orig = createFerie(u, d, d.plusDays(1));
        mvc.perform(post("/api/requests/" + orig + "/approve").header("Authorization", tl)).andExpect(status().isOk());

        // L'utente chiede la rettifica: nuova richiesta in attesa, l'originale resta approvata
        MvcResult r = mvc.perform(post("/api/requests/" + orig + "/amend").header("Authorization", u)
                .contentType(MediaType.APPLICATION_JSON).content(period(d.plusDays(2), d.plusDays(3))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.amends.id").value(orig)).andReturn();
        long am1 = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
        assertThat(statusOf(u, orig)).isEqualTo("APPROVED");
        // Una sola rettifica in attesa alla volta; uguale all'originale no
        mvc.perform(post("/api/requests/" + orig + "/amend").header("Authorization", u)
            .contentType(MediaType.APPLICATION_JSON).content(period(d.plusDays(4), d.plusDays(4)))).andExpect(status().isConflict());
        // Solo chi ha competenza decide
        mvc.perform(post("/api/requests/" + am1 + "/approve").header("Authorization", token("pm2"))).andExpect(status().isForbidden());
        // Rifiutata: resta tutto com'era
        mvc.perform(post("/api/requests/" + am1 + "/reject").header("Authorization", tl)
            .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Settimana di rilascio\"}")).andExpect(status().isOk());
        assertThat(statusOf(u, orig)).isEqualTo("APPROVED");
        mvc.perform(post("/api/requests/" + orig + "/amend").header("Authorization", u)
            .contentType(MediaType.APPLICATION_JSON).content(period(d, d.plusDays(1)))).andExpect(status().isBadRequest());

        // Nuova rettifica approvata: sostituisce l'originale
        r = mvc.perform(post("/api/requests/" + orig + "/amend").header("Authorization", u)
                .contentType(MediaType.APPLICATION_JSON).content(period(d.plusDays(1), d.plusDays(3))))
            .andExpect(status().isOk()).andReturn();
        long am2 = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/requests/" + am2 + "/approve").header("Authorization", tl))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(statusOf(u, orig)).isEqualTo("CANCELLED");

        // Altri utenti non possono rettificare
        mvc.perform(post("/api/requests/" + am2 + "/amend").header("Authorization", token("sara"))
            .contentType(MediaType.APPLICATION_JSON).content(period(d.plusDays(4), d.plusDays(4)))).andExpect(status().isForbidden());
        // Il responsabile rettifica subito
        r = mvc.perform(post("/api/requests/" + am2 + "/amend").header("Authorization", tl)
                .contentType(MediaType.APPLICATION_JSON).content(period(d.plusDays(4), d.plusDays(4))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.decidedBy").exists()).andReturn();
        long am3 = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
        assertThat(statusOf(u, am2)).isEqualTo("CANCELLED");

        // Annullamento diretto: solo responsabile, con motivo
        mvc.perform(post("/api/requests/" + am3 + "/revoke").header("Authorization", u)
            .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/requests/" + am3 + "/revoke").header("Authorization", tl)
            .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/requests/" + am3 + "/revoke").header("Authorization", token("admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Chiesto dall'interessato\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
