package com.albaraka.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for deactivate / reactivate: the reason is mandatory for the audit trail. */
public record StatusChangeRequest(@NotBlank @Size(max = 512) String reason) {
}
