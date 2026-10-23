package com.albaraka.backend.dto;

import com.albaraka.backend.model.CatalogRelease;

import java.time.LocalDateTime;

public record CatalogReleaseResponse(
        Long id, String version, String sealedBy, LocalDateTime sealedAt,
        int exemplarCount, String note, boolean published) {

    public static CatalogReleaseResponse from(CatalogRelease r, boolean published) {
        return new CatalogReleaseResponse(r.getId(), r.getVersion(), r.getSealedBy(), r.getSealedAt(),
                r.getExemplarCount(), r.getNote(), published);
    }
}
