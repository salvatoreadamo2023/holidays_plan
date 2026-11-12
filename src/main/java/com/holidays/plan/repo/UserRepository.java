package com.holidays.plan.repo;

import com.holidays.plan.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = {"area", "team"})
    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"area", "team"})
    List<User> findByAreaIdAndActiveTrueOrderByFullName(Long areaId);

    @EntityGraph(attributePaths = {"area", "team"})
    List<User> findByActiveTrueOrderByFullName();

    @EntityGraph(attributePaths = {"area", "team"})
    List<User> findAllByOrderByFullName();
}
