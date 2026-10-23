package com.albaraka.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** A sealed, immutable catalog version. */
@Entity
@Table(name = "catalog_release")
public class CatalogRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String version;

    @Column(name = "sealed_by")
    private String sealedBy;

    @Column(name = "sealed_at", nullable = false)
    private LocalDateTime sealedAt = LocalDateTime.now();

    @Column(name = "exemplar_count", nullable = false)
    private int exemplarCount;

    @Column(length = 512)
    private String note;

    public Long getId() { return id; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getSealedBy() { return sealedBy; }
    public void setSealedBy(String sealedBy) { this.sealedBy = sealedBy; }
    public LocalDateTime getSealedAt() { return sealedAt; }
    public int getExemplarCount() { return exemplarCount; }
    public void setExemplarCount(int exemplarCount) { this.exemplarCount = exemplarCount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
