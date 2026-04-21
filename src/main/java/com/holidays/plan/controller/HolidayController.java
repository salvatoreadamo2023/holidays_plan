package com.holidays.plan.controller;

import com.holidays.plan.model.HolidayRequest;
import com.holidays.plan.model.RequestStatus;
import com.holidays.plan.service.HolidayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    @Autowired
    private HolidayService holidayService;

    // L'utente crea una nuova richiesta
    @PostMapping("/request")
    public ResponseEntity<?> createRequest(@RequestBody HolidayRequest request) {
        try {
            return ResponseEntity.ok(holidayService.createRequest(request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Il Manager vede le richieste del suo team
    @GetMapping("/manager/{managerId}")
    public List<HolidayRequest> getTeamRequests(@PathVariable Long managerId) {
        return holidayService.getRequestsForManager(managerId);
    }

    // Il Manager approva o rifiuta
    @PutMapping("/status/{requestId}")
    public ResponseEntity<HolidayRequest> updateStatus(
            @PathVariable Long requestId, 
            @RequestParam RequestStatus status) {
        return ResponseEntity.ok(holidayService.updateStatus(requestId, status));
    }
}