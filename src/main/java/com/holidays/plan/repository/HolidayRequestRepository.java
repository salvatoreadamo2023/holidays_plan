package com.holidays.plan.repository;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.holidays.plan.model.HolidayRequest;
import com.holidays.plan.model.RequestStatus;

public interface HolidayRequestRepository extends JpaRepository<HolidayRequest, Long> {
    List<HolidayRequest> findByUserId(Long userId);
    
    // Conta le richieste PENDING per la regola delle max 5
    long countByUserIdAndStato(Long userId, RequestStatus stato);
    
    // Trova le richieste degli utenti che appartengono a un certo manager
    List<HolidayRequest> findByUser_Manager_Id(Long managerId);
}