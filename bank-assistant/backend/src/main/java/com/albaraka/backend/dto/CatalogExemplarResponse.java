package com.albaraka.backend.dto;

import com.albaraka.backend.model.CatalogExemplar;

import java.time.LocalDateTime;

public record CatalogExemplarResponse(
        Long id, String domain, String intent, String text, String language,
        String topic, String slots, boolean active, String updatedBy, LocalDateTime updatedAt) {

    public static CatalogExemplarResponse from(CatalogExemplar e) {
        return new CatalogExemplarResponse(e.getId(), e.getDomain(), e.getIntent(), e.getText(),
                e.getLanguage(), e.getTopic(), e.getSlots(), e.isActive(), e.getUpdatedBy(),
                e.getUpdatedAt());
    }
}
