package com.holidays.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidays.plan.config.DemoDataSeeder;
import com.holidays.plan.service.MailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.base-url=https://ferie.example.com")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class NewFeaturesTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean MailService mail;

    private String login(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))))
            .andExpect(status().isOk()).andReturn();
        return "Bearer " + json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private String token(String username) throws Exception { return login(username, DemoDataSeeder.DEMO_PASSWORD); }

    private static LocalDate weekday(int weeksAhead) {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY)).plusWeeks(weeksAhead);
    }

    @Test
    void impostazioni_orePermessoConsentite() throws Exception {
        String admin = token("admin"), user = token("techlead2");
        mvc.perform(put("/api/admin/settings").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
            .content("{\"permitHours\":[2]}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"permitHours\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"apkUrl\":\"http://non-sicuro\"}")).andExpect(status().isBadRequest());
        try {
            mvc.perform(put("/api/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"permitHours\":[4, 2.0],\"apkUrl\":\"https://expo.dev/artifacts/app.apk\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permitHours.length()").value(2));
            JsonNode s = json.readTree(mvc.perform(get("/api/settings").header("Authorization", user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(s.get("permitHours").toString()).isEqualTo("[2,4]");
            assertThat(s.get("apkUrl").asText()).isEqualTo("https://expo.dev/artifacts/app.apk");
            String day = weekday(30).toString();
            mvc.perform(post("/api/requests").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "PERMESSO", "startDate", day, "endDate", day, "hours", 3))))
                .andExpect(status().isBadRequest());
            mvc.perform(post("/api/requests").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", "PERMESSO", "startDate", day, "endDate", day, "hours", 2))))
                .andExpect(status().isCreated());
            mvc.perform(get("/api/auth/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.apkUrl").value("https://expo.dev/artifacts/app.apk"));
        } finally {
            mvc.perform(put("/api/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("permitHours", List.of(2, 4), "apkUrl", ""))))
                .andExpect(status().isOk());
        }
    }

    @Test
    void campanella_notificheDelleRichieste() throws Exception {
        String user = token("sara"), tl = token("techlead");
        mvc.perform(post("/api/notifications/read-all").header("Authorization", tl)).andExpect(status().isNoContent());
        String day = weekday(31).toString();
        mvc.perform(post("/api/requests").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("type", "FERIE", "startDate", day, "endDate", day))))
            .andExpect(status().isCreated());
        JsonNode inbox = json.readTree(mvc.perform(get("/api/notifications").header("Authorization", tl))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(inbox.get("unread").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode first = inbox.get("items").get(0);
        assertThat(first.get("title").asText()).contains("Sara Colombo");
        assertThat(first.get("screen").asText()).isEqualTo("Approvazioni");
        mvc.perform(post("/api/notifications/" + first.get("id").asLong() + "/read").header("Authorization", user))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread").header("Authorization", tl)).andExpect(jsonPath("$.unread").value(inbox.get("unread").asLong()));
        mvc.perform(post("/api/notifications/read-all").header("Authorization", tl)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread").header("Authorization", tl)).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void resetPassword_conLinkViaEmail() throws Exception {
        when(mail.isAvailable()).thenReturn(true);
        String admin = token("admin");
        mvc.perform(post("/api/admin/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", "reset.utente", "email", "reset@example.com",
                "fullName", "Reset Utente", "password", "Iniziale-2026!", "role", "USER", "areaId", 1))))
            .andExpect(status().isCreated());
        String old = login("reset.utente", "Iniziale-2026!");
        mvc.perform(get("/api/auth/options")).andExpect(jsonPath("$.passwordReset").value(true));

        // Email sbagliata: stessa risposta, nessuna email
        mvc.perform(post("/api/auth/password-forgot").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"reset.utente\",\"email\":\"altro@example.com\"}")).andExpect(status().isAccepted());
        mvc.perform(post("/api/auth/password-forgot").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"nessuno\",\"email\":\"x@example.com\"}")).andExpect(status().isAccepted());
        verify(mail, never()).send(anyString(), anyString(), anyString(), any());

        mvc.perform(post("/api/auth/password-forgot").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"Reset.Utente\",\"email\":\" RESET@example.com \"}")).andExpect(status().isAccepted());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq("reset@example.com"), contains("Reimposta"), anyString(), link.capture());
        assertThat(link.getValue()).startsWith("https://ferie.example.com/password-reset?token=");
        String tok = link.getValue().substring(link.getValue().indexOf("token=") + 6);

        mvc.perform(get("/api/auth/password-reset/check").param("token", tok)).andExpect(jsonPath("$.valid").value(true));
        mvc.perform(get("/api/auth/password-reset/check").param("token", "x".repeat(43))).andExpect(jsonPath("$.valid").value(false));
        String body = "{\"token\":\"" + tok + "\",\"password\":\"%s\",\"confirm\":\"%s\"}";
        mvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON).content(body.formatted("Nuova-Password-1", "Diversa-1234")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON).content(body.formatted("corta", "corta")))
            .andExpect(status().isBadRequest());
        Thread.sleep(1100);
        mvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON).content(body.formatted("Nuova-Password-1", "Nuova-Password-1")))
            .andExpect(status().isNoContent());
        // Monouso
        mvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON).content(body.formatted("Altra-Password-2", "Altra-Password-2")))
            .andExpect(status().isBadRequest());
        // La vecchia sessione e' chiusa, la nuova password funziona (e il cambio obbligatorio e' superato)
        mvc.perform(get("/api/auth/me").header("Authorization", old)).andExpect(status().isUnauthorized());
        String fresh = login("reset.utente", "Nuova-Password-1");
        mvc.perform(get("/api/requests/mine").header("Authorization", fresh)).andExpect(status().isOk());
        verify(mail).send(eq("reset@example.com"), contains("Password modificata"), anyString(), isNull());
    }
}
