package com.holidays.plan.service;

import com.holidays.plan.model.User;
import com.holidays.plan.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    public User createUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword())); // Cripta la password!
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> getMySubordinates(Long managerId) {
        return userRepository.findByManagerId(managerId);
    }
 

  
}