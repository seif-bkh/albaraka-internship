package com.albaraka.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Portal "advisor" flows: APPOINTMENT (rendez-vous) or CALLBACK (rappel).
 *
 * <p>APPOINTMENT requires motif + meetingMethod (+ date/time). CALLBACK
 * requires telephone (+ preferred time/message optional). The service enforces
 * the per-kind rules; bean validation covers the shape.
 */
public record AdvisorRequestRequest(
        @NotBlank @Pattern(regexp = "APPOINTMENT|CALLBACK") String kind,
        @Size(max = 200) String motif,
        @Pattern(regexp = "AGENCE|TELEPHONE|") String meetingMethod,
        @Size(max = 200) String preferredBranch,
        LocalDate preferredDate,
        @Pattern(regexp = "\\d{2}:\\d{2}|") String preferredTime,
        @Size(max = 32) String telephone,
        @Size(max = 2000) String message) {
}
