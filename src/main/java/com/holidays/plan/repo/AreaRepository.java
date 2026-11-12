package com.holidays.plan.repo;

import com.holidays.plan.domain.Area;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AreaRepository extends JpaRepository<Area, Long> {
    List<Area> findAllByOrderByName();
}
