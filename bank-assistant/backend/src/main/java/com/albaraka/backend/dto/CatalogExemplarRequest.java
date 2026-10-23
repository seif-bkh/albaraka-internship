package com.albaraka.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CatalogExemplarRequest(
        @NotBlank @Size(max = 40) String domain,
        @NotBlank @Size(max = 40) String intent,
        @NotBlank @Size(max = 512) String text,
        @Size(max = 5) String language,
        @Size(max = 120) String topic,
        @Size(max = 255) String slots) {
}
