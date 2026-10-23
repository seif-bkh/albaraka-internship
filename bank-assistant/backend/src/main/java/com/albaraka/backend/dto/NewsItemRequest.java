package com.albaraka.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Back-office create/update payload for a news item. */
public record NewsItemRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4000) String body,
        @Size(max = 500) String externalLink,
        @NotNull LocalDate publishedAt,
        Boolean active) {
}
