package com.albaraka.backend.dto;

import com.albaraka.backend.model.BusinessFact;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BusinessFactResponse(
        Long id, String domain, String key, String value, String factType,
        String topic, String language, String source, boolean active,
        LocalDate validFrom, String updatedBy, LocalDateTime updatedAt) {

    public static BusinessFactResponse from(BusinessFact f) {
        return new BusinessFactResponse(f.getId(), f.getDomain(), f.getKey(), f.getValue(),
                f.getFactType(), f.getTopic(), f.getLanguage(), f.getSource(), f.isActive(),
                f.getValidFrom(), f.getUpdatedBy(), f.getUpdatedAt());
    }
}
