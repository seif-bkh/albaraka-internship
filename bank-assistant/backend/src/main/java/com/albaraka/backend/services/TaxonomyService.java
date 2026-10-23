package com.albaraka.backend.services;

import com.albaraka.backend.dto.CatalogExemplarRequest;
import com.albaraka.backend.model.CatalogExemplar;
import com.albaraka.backend.model.CatalogRelease;
import com.albaraka.backend.model.CatalogReleaseExemplar;
import com.albaraka.backend.repository.CatalogExemplarRepository;
import com.albaraka.backend.repository.CatalogReleaseExemplarRepository;
import com.albaraka.backend.repository.CatalogReleaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Curates the classifier's exemplar catalog and seals releases (Phase 3.7 / Slice B).
 *
 * <p>Edits touch only the working copy ({@code catalog_exemplar}). Nothing reaches
 * the live agent until {@link #seal} freezes the active exemplars under a new
 * version and publishes it. Version numbering follows the agent's
 * {@code albaraka.release.bump_version}: increment the last dotted component
 * ({@code 2026.09.1 -> 2026.09.2}).
 */
@Service
public class TaxonomyService {

    /** Mirrors albaraka.core.enums.Domain. */
    public static final Set<String> DOMAINS = Set.of(
            "financement", "comptes", "cartes", "conformite", "service",
            "conversationnel", "hors_perimetre");

    /** Mirrors albaraka.core.enums.Intent. */
    public static final Set<String> INTENTS = Set.of(
            "overview", "describe", "compare", "rate", "eligibility", "documents",
            "procedure", "location", "compliance", "greeting", "reject");

    private static final Set<String> LANGUAGES = Set.of("fr", "ar", "en");

    /** Version used when the DB has no release yet (matches the agent's default). */
    private static final String INITIAL_VERSION = "2026.09.3";

    private final CatalogExemplarRepository exemplars;
    private final CatalogReleaseRepository releases;
    private final CatalogReleaseExemplarRepository frozen;

    public TaxonomyService(CatalogExemplarRepository exemplars,
                           CatalogReleaseRepository releases,
                           CatalogReleaseExemplarRepository frozen) {
        this.exemplars = exemplars;
        this.releases = releases;
        this.frozen = frozen;
    }

    // ---------------------------------------------------------------- exemplars

    public List<CatalogExemplar> list(String domain) {
        if (domain == null || domain.isBlank()) {
            return exemplars.findAllByOrderByDomainAscIntentAscIdAsc();
        }
        return exemplars.findByDomainOrderByIntentAscIdAsc(domain.trim().toLowerCase());
    }

    public CatalogExemplar get(Long id) {
        return exemplars.findById(id).orElseThrow(NoSuchElementException::new);
    }

    @Transactional
    public CatalogExemplar create(CatalogExemplarRequest req, String actor) {
        String domain = norm(req.domain());
        String intent = norm(req.intent());
        String language = req.language() == null || req.language().isBlank() ? "fr" : req.language();
        validate(domain, intent, language);
        String text = req.text().trim();
        if (exemplars.existsByDomainAndIntentAndLanguageAndText(domain, intent, language, text)) {
            throw new IllegalArgumentException("Cet exemplaire existe déjà pour " + domain + "/" + intent);
        }
        CatalogExemplar e = new CatalogExemplar();
        e.setDomain(domain);
        e.setIntent(intent);
        e.setLanguage(language);
        e.setText(text);
        e.setTopic(blankToNull(req.topic()));
        e.setSlots(blankToNull(req.slots()));
        e.setUpdatedBy(actor);
        return exemplars.save(e);
    }

    @Transactional
    public CatalogExemplar update(Long id, CatalogExemplarRequest req, String actor) {
        CatalogExemplar e = get(id);
        String domain = norm(req.domain());
        String intent = norm(req.intent());
        String language = req.language() == null || req.language().isBlank() ? "fr" : req.language();
        validate(domain, intent, language);
        e.setDomain(domain);
        e.setIntent(intent);
        e.setLanguage(language);
        e.setText(req.text().trim());
        e.setTopic(blankToNull(req.topic()));
        e.setSlots(blankToNull(req.slots()));
        e.setUpdatedBy(actor);
        return exemplars.save(e);
    }

    @Transactional
    public CatalogExemplar setActive(Long id, boolean active, String actor) {
        CatalogExemplar e = get(id);
        e.setActive(active);
        e.setUpdatedBy(actor);
        return exemplars.save(e);
    }

    // ----------------------------------------------------------------- releases

    public List<CatalogRelease> releases() {
        return releases.findAllByOrderBySealedAtDesc();
    }

    public List<CatalogReleaseExemplar> releaseContent(Long releaseId) {
        releases.findById(releaseId).orElseThrow(NoSuchElementException::new);
        return frozen.findByReleaseIdOrderByDomainAscIntentAscIdAsc(releaseId);
    }

    /**
     * The release in force: the newest one sealed. There is no second engine to
     * hand a version to any more, so "published" means "the latest seal", and
     * rolling back to an older version is no longer an operation that would change
     * anything.
     */
    public String publishedVersion() {
        return releases.findTopByOrderBySealedAtDescIdDesc().map(CatalogRelease::getVersion).orElse(null);
    }

    /**
     * Freeze the active exemplars as a new release, one version above the latest.
     * The new release is the one in force once sealed.
     */
    @Transactional
    public CatalogRelease seal(String note, String actor) {
        List<CatalogExemplar> active = exemplars.findByActiveTrueOrderByDomainAscIntentAscIdAsc();
        if (active.isEmpty()) {
            throw new IllegalArgumentException("Impossible de sceller un catalogue sans exemplaire actif.");
        }
        String base = releases.findTopByOrderBySealedAtDescIdDesc()
                .map(CatalogRelease::getVersion).orElse(INITIAL_VERSION);
        String version = bumpVersion(base);

        CatalogRelease r = new CatalogRelease();
        r.setVersion(version);
        r.setSealedBy(actor);
        r.setExemplarCount(active.size());
        r.setNote(blankToNull(note));
        r = releases.save(r);
        final Long releaseId = r.getId();
        frozen.saveAll(active.stream().map(e -> CatalogReleaseExemplar.freeze(releaseId, e)).toList());

        return r;
    }

    /** Same rule as albaraka.release.bump_version. */
    static String bumpVersion(String version) {
        String[] parts = version.split("\\.");
        try {
            parts[parts.length - 1] = String.valueOf(Integer.parseInt(parts[parts.length - 1]) + 1);
            return String.join(".", parts);
        } catch (NumberFormatException e) {
            return version + ".1";
        }
    }

    private static void validate(String domain, String intent, String language) {
        if (!DOMAINS.contains(domain)) {
            throw new IllegalArgumentException("domain invalide: " + domain + " (attendu: " + DOMAINS + ")");
        }
        if (!INTENTS.contains(intent)) {
            throw new IllegalArgumentException("intent invalide: " + intent + " (attendu: " + INTENTS + ")");
        }
        if (!LANGUAGES.contains(language)) {
            throw new IllegalArgumentException("language invalide: " + language + " (attendu: " + LANGUAGES + ")");
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
