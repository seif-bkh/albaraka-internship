package com.albaraka.backend.dto;

import com.albaraka.backend.model.AdvisorRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Confirmation returned after an advisor request is persisted. */
public record AdvisorRequestResponse(
        Long id,
        String kind,
        String status,
        String motif,
        String meetingMethod,
        String preferredBranch,
        LocalDate preferredDate,
        String preferredTime,
        String telephone,
        String message,
        LocalDateTime createdAt) {

    public static AdvisorRequestResponse from(AdvisorRequest r) {
        return new AdvisorRequestResponse(
                r.getId(),
                r.getKind().name(),
                r.getStatus().name(),
                r.getMotif(),
                r.getMeetingMethod(),
                r.getPreferredBranch(),
                r.getPreferredDate(),
                r.getPreferredTime(),
                r.getTelephone(),
                r.getMessage(),
                r.getCreatedAt());
    }
}
