package com.albaraka.backend.dto;

import com.albaraka.backend.model.BusinessFactAudit;

import java.time.LocalDateTime;

public record BusinessFactAuditResponse(
        Long id, Long factId, String domain, String key, String action,
        String oldValue, String newValue, String reason, String changedBy,
        LocalDateTime changedAt) {

    public static BusinessFactAuditResponse from(BusinessFactAudit a) {
        return new BusinessFactAuditResponse(a.getId(), a.getFactId(), a.getDomain(), a.getKey(),
                a.getAction(), a.getOldValue(), a.getNewValue(), a.getReason(), a.getChangedBy(),
                a.getChangedAt());
    }
}
