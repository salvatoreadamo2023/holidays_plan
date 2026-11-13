package com.holidays.plan.web;

import com.holidays.plan.security.CurrentUser;
import com.holidays.plan.service.RequestService;
import com.holidays.plan.web.dto.RequestDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/requests")
public class RequestController {
    private final RequestService service;
    private final CurrentUser currentUser;
    private final RequestViewer views;

    public RequestController(RequestService service, CurrentUser currentUser, RequestViewer views) {
        this.service = service;
        this.currentUser = currentUser;
        this.views = views;
    }

    @PostMapping("/preview")
    @Transactional(readOnly = true)
    public Preview preview(@Valid @RequestBody CreateRequest in) {
        return service.preview(currentUser.get(), in);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RequestView create(@Valid @RequestBody CreateRequest in) {
        return views.one(service.create(currentUser.get(), in));
    }

    /** Persone per cui l'utente (Admin/PM) puo' inserire un'assenza gia' approvata. */
    @GetMapping("/on-behalf/people")
    @Transactional(readOnly = true)
    public List<com.holidays.plan.web.dto.UserSummary> onBehalfPeople() {
        return service.insertableFor(currentUser.get()).stream().map(com.holidays.plan.web.dto.UserSummary::of).toList();
    }

    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public List<RequestView> mine() {
        return views.many(service.mine(currentUser.get()));
    }

    @GetMapping("/to-approve")
    @Transactional(readOnly = true)
    public List<RequestView> toApprove() {
        return views.many(service.toDecide(currentUser.get()));
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public RequestView approve(@PathVariable Long id, @Valid @RequestBody(required = false) Decision d) {
        return views.one(service.approve(currentUser.get(), id, d == null ? null : d.note()));
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public RequestView reject(@PathVariable Long id, @Valid @RequestBody Decision d) {
        return views.one(service.reject(currentUser.get(), id, d.note()));
    }

    @PostMapping("/{id}/withdraw")
    @Transactional
    public RequestView withdraw(@PathVariable Long id) {
        return views.one(service.withdraw(currentUser.get(), id));
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public RequestView requestCancel(@PathVariable Long id, @Valid @RequestBody(required = false) Decision d) {
        return views.one(service.requestCancel(currentUser.get(), id, d == null ? null : d.note()));
    }

    @PostMapping("/{id}/convert")
    @Transactional
    public RequestView convert(@PathVariable Long id) {
        return views.one(service.convertTentative(currentUser.get(), id));
    }

    @PostMapping("/{id}/cancel/accept")
    @Transactional
    public RequestView acceptCancel(@PathVariable Long id, @Valid @RequestBody(required = false) Decision d) {
        return views.one(service.decideCancel(currentUser.get(), id, true, d == null ? null : d.note()));
    }

    @PostMapping("/{id}/cancel/deny")
    @Transactional
    public RequestView denyCancel(@PathVariable Long id, @Valid @RequestBody(required = false) Decision d) {
        return views.one(service.decideCancel(currentUser.get(), id, false, d == null ? null : d.note()));
    }
}
