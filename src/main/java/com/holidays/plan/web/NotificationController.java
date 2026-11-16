package com.holidays.plan.web;

import com.holidays.plan.security.CurrentUser;
import com.holidays.plan.service.NotificationService;
import com.holidays.plan.service.NotificationService.Inbox;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Campanella del sito: le stesse notifiche che l'app riceve come push. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Inbox inbox(@RequestParam(defaultValue = "30") int limit) {
        return service.inbox(currentUser.get(), limit);
    }

    @GetMapping("/unread")
    public Map<String, Long> unread() {
        return Map.of("unread", service.unread(currentUser.get()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable Long id) {
        service.markRead(currentUser.get(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll() {
        service.markAllRead(currentUser.get());
    }
}
