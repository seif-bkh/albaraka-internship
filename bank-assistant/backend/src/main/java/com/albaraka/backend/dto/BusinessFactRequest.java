package com.albaraka.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Create / update payload for a governed fact. {@code reason} is stored in the
 * audit trail; it is optional on create and required on update / deactivate.
 */
public record BusinessFactRequest(
        @NotBlank @Size(max = 40) String domain,
        @NotBlank @Size(max = 120)
        @Pattern(regexp = "[a-z0-9_]+", message = "key: lettres minuscules, chiffres et _ uniquement")
        String key,
        @NotBlank String value,
        @NotBlank @Size(max = 40) String factType,
        @Size(max = 120) String topic,
        @Size(max = 5) String language,
        @Size(max = 512) String source,
        LocalDate validFrom,
        @Size(max = 512) String reason) {
}
