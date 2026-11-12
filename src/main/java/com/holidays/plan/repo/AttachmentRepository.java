package com.holidays.plan.repo;

import com.holidays.plan.domain.Attachment;
import com.holidays.plan.web.dto.AttachmentInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    @Query("""
            select new com.holidays.plan.web.dto.AttachmentInfo(a.id, a.request.id, a.filename, a.contentType,
                a.sizeBytes, a.createdAt)
            from Attachment a where a.request.id in :ids order by a.id""")
    List<AttachmentInfo> infoFor(@Param("ids") Collection<Long> requestIds);

    @Query("select count(a) from Attachment a where a.request.id = :id")
    long countFor(@Param("id") Long requestId);
}
