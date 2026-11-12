package com.holidays.plan.web;

import com.holidays.plan.domain.AbsenceRequest;
import com.holidays.plan.repo.AttachmentRepository;
import com.holidays.plan.web.dto.AttachmentInfo;
import com.holidays.plan.web.dto.RequestDtos.RequestView;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Costruisce le RequestView con i loro allegati, con una sola query per elenco. */
@Component
public class RequestViewer {
    private final AttachmentRepository attachments;

    public RequestViewer(AttachmentRepository attachments) {
        this.attachments = attachments;
    }

    public RequestView one(AbsenceRequest r) {
        return many(List.of(r)).get(0);
    }

    public List<RequestView> many(List<AbsenceRequest> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, List<AttachmentInfo>> byRequest = attachments
            .infoFor(list.stream().map(AbsenceRequest::getId).toList()).stream()
            .collect(Collectors.groupingBy(AttachmentInfo::requestId));
        return list.stream().map(r -> RequestView.of(r, byRequest.getOrDefault(r.getId(), List.of()))).toList();
    }
}
