package com.holidays.plan.repo;

import com.holidays.plan.domain.PayrollItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PayrollItemRepository extends JpaRepository<PayrollItem, PayrollItem.Key> {
    List<PayrollItem> findAllByOrderByUserIdAscCodeAsc();

    @Modifying
    @Query("delete from PayrollItem p where p.userId = :userId")
    void deleteByUser(@Param("userId") Long userId);
}
