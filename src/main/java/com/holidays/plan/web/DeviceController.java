package com.holidays.plan.web;

import com.holidays.plan.security.CurrentUser;
import com.holidays.plan.service.PushService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Il telefono registra qui il suo token per ricevere le notifiche push. */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final PushService push;
    private final CurrentUser currentUser;

    public DeviceController(PushService push, CurrentUser currentUser) {
        this.push = push;
        this.currentUser = currentUser;
    }

    public record DeviceInput(@NotBlank @Size(max = 200) String token, @Size(max = 20) String platform) {}

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody DeviceInput in) {
        push.register(currentUser.get().getId(), in.token(), in.platform());
    }

    /** Alla disconnessione: il telefono smette di ricevere notifiche per questo utente. */
    @PostMapping("/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@Valid @RequestBody DeviceInput in) {
        push.unregister(currentUser.getAllowingPasswordChange().getId(), in.token());
    }
}
