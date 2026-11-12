package com.holidays.plan.web;

import com.holidays.plan.domain.Attachment;
import com.holidays.plan.security.CurrentUser;
import com.holidays.plan.service.AttachmentService;
import com.holidays.plan.service.AttachmentService.SignedLink;
import com.holidays.plan.web.dto.AttachmentInfo;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AttachmentController {
    private final AttachmentService service;
    private final CurrentUser currentUser;

    public AttachmentController(AttachmentService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping(path = "/requests/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentInfo upload(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return service.upload(currentUser.get(), id, file);
    }

    @GetMapping("/requests/{id}/attachments")
    public List<AttachmentInfo> list(@PathVariable Long id) {
        return service.list(currentUser.get(), id);
    }

    @DeleteMapping("/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(currentUser.get(), id);
    }

    @GetMapping("/attachments/{id}/link")
    public SignedLink link(@PathVariable Long id) {
        return service.link(currentUser.get(), id);
    }

    /** Pubblico ma protetto dalla firma con scadenza (vedi AttachmentService). */
    @GetMapping("/files/{id}")
    public ResponseEntity<byte[]> file(@PathVariable Long id, @RequestParam long exp, @RequestParam String sig) {
        Attachment a = service.download(id, exp, sig);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(a.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(a.getFilename()).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(a.getData());
    }
}
