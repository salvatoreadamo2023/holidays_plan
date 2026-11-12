package com.holidays.plan.service;

import com.holidays.plan.domain.AbsenceRequest;
import com.holidays.plan.domain.Attachment;
import com.holidays.plan.domain.RequestStatus;
import com.holidays.plan.domain.Role;
import com.holidays.plan.domain.User;
import com.holidays.plan.repo.AbsenceRequestRepository;
import com.holidays.plan.repo.AttachmentRepository;
import com.holidays.plan.web.dto.AttachmentInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Allegati alle assenze. Li vede chi puo' vedere i dettagli della richiesta
 * (il titolare, i responsabili della sua area, l'Admin); li carica e li toglie il titolare.
 * Il download avviene con un link firmato che scade dopo pochi minuti, cosi'
 * l'app puo' aprirlo nel browser del telefono senza passare il token di login.
 */
@Service
public class AttachmentService {
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_PER_REQUEST = 5;
    static final long LINK_TTL_SECONDS = 300;

    private final AttachmentRepository attachments;
    private final AbsenceRequestRepository requests;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final SecretKey key;
    private final Clock clock;

    public AttachmentService(AttachmentRepository attachments, AbsenceRequestRepository requests,
                             AccessPolicy policy, AuditService audit, SecretKey jwtKey, Clock clock) {
        this.attachments = attachments;
        this.requests = requests;
        this.policy = policy;
        this.audit = audit;
        this.key = jwtKey;
        this.clock = clock;
    }

    public record SignedLink(String url, Instant expiresAt) {}

    private AbsenceRequest loadRequest(Long id) {
        return requests.findById(id).orElseThrow(ApiException::notFound);
    }

    private Attachment loadAttachment(Long id) {
        return attachments.findById(id).orElseThrow(ApiException::notFound);
    }

    private boolean isOwner(User actor, AbsenceRequest r) {
        return Objects.equals(actor.getId(), r.getUser().getId());
    }

    private void checkCanSee(User actor, AbsenceRequest r) {
        if (!policy.canSeeDetails(actor, r.getUser())) throw ApiException.notFound();
    }

    @Transactional
    public AttachmentInfo upload(User actor, Long requestId, MultipartFile file) {
        AbsenceRequest r = loadRequest(requestId);
        checkCanSee(actor, r);
        if (!isOwner(actor, r) && actor.getRole() != Role.ADMIN) throw ApiException.forbidden();
        if (r.getStatus() == RequestStatus.WITHDRAWN) throw ApiException.conflict("La richiesta e' stata ritirata");
        if (file == null || file.isEmpty()) throw ApiException.bad("Il file e' vuoto");
        if (file.getSize() > MAX_BYTES) throw ApiException.bad("Il file supera i 5 MB");
        if (attachments.countFor(requestId) >= MAX_PER_REQUEST)
            throw ApiException.conflict("Puoi allegare al massimo " + MAX_PER_REQUEST + " file per richiesta");
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.bad("File non leggibile");
        }
        String type = detectType(data);
        if (type == null) throw ApiException.bad("Formato non ammesso: carica un PDF, JPG o PNG");

        Attachment a = new Attachment();
        a.setRequest(r);
        a.setFilename(safeName(file.getOriginalFilename(), type));
        a.setContentType(type);
        a.setSizeBytes(data.length);
        a.setData(data);
        a.setUploadedById(actor.getId());
        a = attachments.save(a);
        audit.log(actor.getId(), r.getId(), "ATTACHMENT_ADDED", a.getFilename());
        return info(a);
    }

    @Transactional(readOnly = true)
    public List<AttachmentInfo> list(User actor, Long requestId) {
        AbsenceRequest r = loadRequest(requestId);
        checkCanSee(actor, r);
        return attachments.infoFor(List.of(requestId));
    }

    @Transactional
    public void delete(User actor, Long attachmentId) {
        Attachment a = loadAttachment(attachmentId);
        AbsenceRequest r = a.getRequest();
        checkCanSee(actor, r);
        if (!isOwner(actor, r) && actor.getRole() != Role.ADMIN) throw ApiException.forbidden();
        attachments.delete(a);
        audit.log(actor.getId(), r.getId(), "ATTACHMENT_REMOVED", a.getFilename());
    }

    @Transactional(readOnly = true)
    public SignedLink link(User actor, Long attachmentId) {
        Attachment a = loadAttachment(attachmentId);
        checkCanSee(actor, a.getRequest());
        long exp = Instant.now(clock).getEpochSecond() + LINK_TTL_SECONDS;
        return new SignedLink("/api/files/" + a.getId() + "?exp=" + exp + "&sig=" + sign(a.getId(), exp),
            Instant.ofEpochSecond(exp));
    }

    /** Download tramite link firmato: non serve il login, la firma fa da permesso. */
    @Transactional(readOnly = true)
    public Attachment download(Long attachmentId, long exp, String sig) {
        if (sig == null || exp < Instant.now(clock).getEpochSecond())
            throw new ApiException(org.springframework.http.HttpStatus.GONE, "Link scaduto, riaprilo dall'app");
        byte[] expected = sign(attachmentId, exp).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.US_ASCII))) throw ApiException.notFound();
        return loadAttachment(attachmentId);
    }

    private String sign(Long id, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] out = mac.doFinal(("attachment:" + id + ":" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Firma non disponibile", e);
        }
    }

    private static AttachmentInfo info(Attachment a) {
        return new AttachmentInfo(a.getId(), a.getRequest().getId(), a.getFilename(), a.getContentType(),
            a.getSizeBytes(), a.getCreatedAt());
    }

    /** Il tipo si decide dal contenuto, non da quello che dichiara il client. */
    static String detectType(byte[] d) {
        if (d.length >= 4 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F') return "application/pdf";
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "image/jpeg";
        return null;
    }

    static String safeName(String original, String type) {
        String ext = switch (type) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            default -> ".jpg";
        };
        String n = original == null ? "" : original;
        n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1);
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        n = n.replaceAll("[^A-Za-z0-9 ._-]", "_").replaceAll("\\s+", " ").trim();
        if (n.isEmpty()) n = "allegato";
        if (n.length() > 100) n = n.substring(0, 100);
        return n + ext;
    }
}
