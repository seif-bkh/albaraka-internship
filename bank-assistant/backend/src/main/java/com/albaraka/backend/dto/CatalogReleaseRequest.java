package com.albaraka.backend.dto;

import jakarta.validation.constraints.Size;

public record CatalogReleaseRequest(@Size(max = 512) String note) {
}
