package com.holidays.plan.repo;

import com.holidays.plan.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByAreaIdOrderByName(Long areaId);

    List<Team> findAllByOrderByName();
}
