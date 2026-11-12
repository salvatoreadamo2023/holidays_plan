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

    public RequestController(RequestService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
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
        return RequestView.of(service.create(currentUser.get(), in));
    }

    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public List<RequestView> mine() {
        return service.mine(currentUser.get()).stream().map(RequestView::of).toList();
    }

    @GetMapping("/to-approve")
    @Transactional(readOnly = true)
    public List<RequestView> toApprove() {
        return service.toDecide(currentUser.get()).stream().map(RequestView::of).toList();
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public RequestView approve(@PathVariable Long id, @Valid @RequestBody(required = false) Decision d) {
        return RequestView.of(service.approve(currentUser.get(), id, d == null ? null : d.note()));
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public RequestView reject(@PathVariable Long id, @Valid @RequestBody Decision d) {
        return RequestView.of(service.reject(currentUser.get(), id, d.note()));
    }

    @PostMapping("/{id}/withdraw")
    @Transactional
    public RequestView withdraw(@PathVariable Long id) {
        return RequestView.of(service.withdraw(currentUser.get(), id));
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public RequestView requestCancel(@PathVariable Long id, @Valid @RequestBody(required = false) Decision d) {
        return RequestView.of(service.requestCancel(currentUser.get(), id, d == null ? null : d.note()));
    }

    @PostMapping("/{id}/cancel/accept")
    @Transactional
    public RequestView acceptCancel(@PathVariable Long id, @Valid @RequestBody(required = false) Decision d) {
        return RequestView.of(service.decideCancel(currentUser.get(), id, true, d == null ? null : d.note()));
    }

    @PostMapping("/{id}/cancel/deny")
    @Transactional
    public RequestView denyCancel(@PathVariable Long id, @Valid @RequestBody(required = false) Decision d) {
        return RequestView.of(service.decideCancel(currentUser.get(), id, false, d == null ? null : d.note()));
    }
}
