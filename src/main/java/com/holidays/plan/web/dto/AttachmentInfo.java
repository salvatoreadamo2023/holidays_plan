package com.holidays.plan.web.dto;

import java.time.Instant;

public record AttachmentInfo(Long id, Long requestId, String filename, String contentType,
                             long sizeBytes, Instant createdAt) {}
