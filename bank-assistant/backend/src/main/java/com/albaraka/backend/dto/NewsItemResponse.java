package com.albaraka.backend.dto;

import com.albaraka.backend.model.NewsItem;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record NewsItemResponse(
        Long id,
        String title,
        String body,
        String externalLink,
        LocalDate publishedAt,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static NewsItemResponse from(NewsItem n) {
        return new NewsItemResponse(
                n.getId(), n.getTitle(), n.getBody(), n.getExternalLink(),
                n.getPublishedAt(), n.isActive(), n.getCreatedAt(), n.getUpdatedAt());
    }
}
