package com.holidays.plan.repo;

import com.holidays.plan.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    @Query("select u from User u left join u.area a where u.active = true and (u.role = com.holidays.plan.domain.Role.ADMIN or (a.id = :areaId and u.role in (com.holidays.plan.domain.Role.TEAM_LEAD, com.holidays.plan.domain.Role.PROJECT_MANAGER)))")
    List<User> possibleApprovers(@org.springframework.data.repository.query.Param("areaId") Long areaId);
}
