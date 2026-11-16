package com.holidays.plan.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Invio email tramite SMTP (Amazon SES, come Digisol): SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD,
 * MAIL_ENABLED=true e MAIL_FROM con un indirizzo di un dominio verificato in SES.
 * Coda su un solo thread, invio dopo il commit, mai eccezioni verso il chiamante.
 */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+'-]{1,64}@[A-Za-z0-9.-]{1,180}\\.[A-Za-z]{2,24}$");
    private static final String FOOTER = "Email automatica di Gestione Ferie: non rispondere a questo messaggio.";

    private final ObjectProvider<JavaMailSender> sender;
    private final boolean enabled;
    private final String from;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(1000), r -> { Thread t = new Thread(r, "mail"); t.setDaemon(true); return t; },
        new ThreadPoolExecutor.DiscardPolicy());

    public MailService(ObjectProvider<JavaMailSender> sender,
                       @Value("${app.mail.enabled:false}") boolean enabled,
                       @Value("${app.mail.from:}") String from) {
        this.sender = sender;
        this.enabled = enabled;
        this.from = from == null ? "" : from.trim();
    }

    /** Vero se l'invio e' configurato: abilitato, mittente valido e server SMTP impostato. */
    public boolean isAvailable() {
        return enabled && EMAIL.matcher(from).matches() && sender.getIfAvailable() != null;
    }

    public static boolean validAddress(String a) {
        return a != null && EMAIL.matcher(a.trim()).matches();
    }

    /** Accoda una email (testo + HTML); parte dopo il commit della transazione, se ce n'e' una. */
    public void send(String to, String subject, String text, String link) {
        if (!isAvailable() || !validAddress(to) || subject == null || subject.contains("\n") || subject.contains("\r")) return;
        Runnable job = () -> executor.execute(() -> deliver(to.trim(), subject, text, link));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { job.run(); }
            });
        } else {
            job.run();
        }
    }

    private void deliver(String to, String subject, String text, String link) {
        try {
            JavaMailSender mail = sender.getObject();
            MimeMessage mime = mail.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(mime, true, "UTF-8");
            h.setFrom(new InternetAddress(from, "Gestione Ferie", "UTF-8"));
            h.setTo(to);
            h.setSubject(subject);
            h.setText(text + "\n\n-- \n" + FOOTER, html(text, link));
            mail.send(mime);
        } catch (Exception e) {
            log.warn("Email non inviata: {}", e.getClass().getSimpleName());
        }
    }

    static String html(String text, String link) {
        String body = HtmlUtils.htmlEscape(text).replace("\n", "<br>");
        if (link != null) {
            String l = HtmlUtils.htmlEscape(link);
            body = body.replace(l, "<a href=\"" + l + "\" style=\"color:#e31937\">" + l + "</a>");
        }
        return "<!doctype html><html><body style=\"margin:0;padding:0;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2933\">"
            + "<div style=\"max-width:640px;margin:0 auto;padding:24px\">"
            + "<div style=\"font-size:20px;font-weight:bold;color:#e31937;margin-bottom:16px\">Gestione Ferie</div>"
            + "<div style=\"background:#ffffff;border-radius:8px;padding:24px;font-size:14px;line-height:1.5\">" + body + "</div>"
            + "<div style=\"font-size:12px;color:#6b7280;margin-top:16px\">" + FOOTER + "</div></div></body></html>";
    }
}
