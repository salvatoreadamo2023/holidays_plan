package com.holidays.plan.controller;

import com.holidays.plan.model.User;
import com.holidays.plan.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    // Solo l'Admin lo userà per creare i dipendenti
    @PostMapping("/create")
    public User createUser(@RequestBody User user) {
        return userService.createUser(user);
    }

    // Per vedere tutti gli utenti (Admin)
    @GetMapping("/all")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    // Per vedere il proprio team
    @GetMapping("/my-team/{managerId}")
    public List<User> getMyTeam(@PathVariable Long managerId) {
        return userService.getMySubordinates(managerId);
    }
}