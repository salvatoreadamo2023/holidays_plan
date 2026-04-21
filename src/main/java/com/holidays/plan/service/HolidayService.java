package com.holidays.plan.service;

import com.holidays.plan.model.*;
import com.holidays.plan.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class HolidayService {

    @Autowired
    private HolidayRequestRepository holidayRepository;

    @Autowired
    private UserRepository userRepository;

    // 1. CREAZIONE RICHIESTA CON LOGICA "MAX 5 PENDING"
    public HolidayRequest createRequest(HolidayRequest request) {
        // Recuperiamo l'utente dal DB per sicurezza
        User user = userRepository.findById(request.getUser().getId())
                .orElseThrow(() -> new RuntimeException("Utente non trovato"));

        // Conta quante richieste PENDING ha l'utente
        long pendingCount = holidayRepository.countByUserIdAndStato(user.getId(), RequestStatus.PENDING);

        if (pendingCount >= 5) {
            throw new RuntimeException("Hai già 5 richieste in attesa. Attendi l'approvazione prima di crearne nuove.");
        }

        // Impostiamo lo stato iniziale
        request.setStato(RequestStatus.PENDING);
        request.setUser(user);

        return holidayRepository.save(request);
    }

    // 2. APPROVAZIONE/RIFIUTO (Solo per Manager/Admin)
    public HolidayRequest updateStatus(Long requestId, RequestStatus newStatus) {
        HolidayRequest request = holidayRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Richiesta non trovata"));

        request.setStato(newStatus);

        // Se approvata, potremmo aggiungere qui la logica per scalare il saldoFerie
        if (newStatus == RequestStatus.APPROVED) {
            // Logica per calcolare i giorni tra le date e sottrarli al saldo dell'utente
        }

        return holidayRepository.save(request);
    }

    // 3. VISTA PER IL MANAGER (Vede le richieste dei suoi subordinati)
    public List<HolidayRequest> getRequestsForManager(Long managerId) {
        return holidayRepository.findByUser_Manager_Id(managerId);
    }
}