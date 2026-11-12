package com.holidays.plan.repo;

import com.holidays.plan.domain.AbsenceRequest;
import com.holidays.plan.domain.AbsenceType;
import com.holidays.plan.domain.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AbsenceRequestRepository extends JpaRepository<AbsenceRequest, Long> {

    @Query("""
            select r from AbsenceRequest r join fetch r.user u left join fetch u.area left join fetch u.team
            where u.id = :userId order by r.startDate desc""")
    List<AbsenceRequest> findByUser(@Param("userId") Long userId);

    @Query("""
            select r from AbsenceRequest r join fetch r.user u left join fetch u.area left join fetch u.team
            where r.status in :statuses and r.startDate <= :to and r.endDate >= :from""")
    List<AbsenceRequest> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                         @Param("statuses") Collection<RequestStatus> statuses);

    @Query("""
            select count(r) > 0 from AbsenceRequest r
            where r.user.id = :userId and r.status in :statuses
              and r.startDate <= :to and r.endDate >= :from""")
    boolean existsOverlapForUser(@Param("userId") Long userId, @Param("from") LocalDate from,
                                 @Param("to") LocalDate to, @Param("statuses") Collection<RequestStatus> statuses);

    @Query("""
            select coalesce(sum(r.amount), 0) from AbsenceRequest r
            where r.user.id = :userId and r.type = :type and r.status in :statuses
              and r.startDate >= :from and r.startDate <= :to""")
    BigDecimal sumAmount(@Param("userId") Long userId, @Param("type") AbsenceType type,
                         @Param("statuses") Collection<RequestStatus> statuses,
                         @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select r from AbsenceRequest r join fetch r.user u left join fetch u.area left join fetch u.team
            where r.status in :statuses order by r.createdAt""")
    List<AbsenceRequest> findByStatusIn(@Param("statuses") Collection<RequestStatus> statuses);
}
