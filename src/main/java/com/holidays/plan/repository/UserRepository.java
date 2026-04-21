package com.holidays.plan.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.holidays.plan.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    List<User> findByManagerId(Long managerId); // Trova il team di un manager
}