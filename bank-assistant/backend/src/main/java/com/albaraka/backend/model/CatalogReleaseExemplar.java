package com.albaraka.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Frozen copy of one exemplar inside a sealed release (what the agent loads). */
@Entity
@Table(name = "catalog_release_exemplar")
public class CatalogReleaseExemplar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "release_id", nullable = false)
    private Long releaseId;

    @Column(nullable = false, length = 40)
    private String domain;

    @Column(nullable = false, length = 40)
    private String intent;

    @Column(nullable = false, length = 512)
    private String text;

    @Column(nullable = false, length = 5)
    private String language;

    @Column(length = 120)
    private String topic;

    @Column(length = 255)
    private String slots;

    public static CatalogReleaseExemplar freeze(Long releaseId, CatalogExemplar e) {
        CatalogReleaseExemplar f = new CatalogReleaseExemplar();
        f.releaseId = releaseId;
        f.domain = e.getDomain();
        f.intent = e.getIntent();
        f.text = e.getText();
        f.language = e.getLanguage();
        f.topic = e.getTopic();
        f.slots = e.getSlots();
        return f;
    }

    public Long getId() { return id; }
    public Long getReleaseId() { return releaseId; }
    public String getDomain() { return domain; }
    public String getIntent() { return intent; }
    public String getText() { return text; }
    public String getLanguage() { return language; }
    public String getTopic() { return topic; }
    public String getSlots() { return slots; }
}
