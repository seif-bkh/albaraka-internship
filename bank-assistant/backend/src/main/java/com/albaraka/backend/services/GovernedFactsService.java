package com.albaraka.backend.services;

import com.albaraka.backend.dto.BusinessFactRequest;
import com.albaraka.backend.model.BusinessFact;
import com.albaraka.backend.model.BusinessFactAudit;
import com.albaraka.backend.repository.BusinessFactAuditRepository;
import com.albaraka.backend.repository.BusinessFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Business rules for the governed-facts store (Phase 3.7 / Slice A).
 *
 * <p>Facts are never hard-deleted: they are deactivated, so the audit trail and
 * any answer that cited them stay explainable. Every mutation writes one
 * {@link BusinessFactAudit} row in the same transaction.
 *
 * <p>{@link #DOMAINS} and {@link #FACT_TYPES} mirror the Python agent's enums
 * ({@code albaraka.core.enums.Domain}) — a value outside these would be silently
 * ignored by the agent, so we reject it here instead.
 */
@Service
public class GovernedFactsService {

    /** Allowed values of {@code domain}; keep in sync with albaraka.core.enums.Domain. */
    public static final Set<String> DOMAINS = Set.of(
            "financement", "comptes", "cartes", "conformite", "service");

    /** Kinds of governed data admins control. */
    public static final Set<String> FACT_TYPES = Set.of(
            "rate", "fee", "terms", "eligibility", "documents", "product", "contact");

    private static final Set<String> LANGUAGES = Set.of("fr", "ar", "en");

    private final BusinessFactRepository facts;
    private final BusinessFactAuditRepository audits;

    public GovernedFactsService(BusinessFactRepository facts, BusinessFactAuditRepository audits) {
        this.facts = facts;
        this.audits = audits;
    }

    public List<BusinessFact> list(String domain) {
        if (domain == null || domain.isBlank()) {
            return facts.findAllByOrderByDomainAscKeyAsc();
        }
        return facts.findByDomainOrderByKeyAsc(normalizeDomain(domain));
    }

    public BusinessFact get(Long id) {
        return facts.findById(id).orElseThrow(NoSuchElementException::new);
    }

    public List<BusinessFactAudit> history(Long id) {
        get(id);
        return audits.findByFactIdOrderByChangedAtDesc(id);
    }

    @Transactional
    public BusinessFact create(BusinessFactRequest req, String actor) {
        String domain = normalizeDomain(req.domain());
        validateEnums(domain, req.factType(), req.language());
        facts.findByDomainAndKey(domain, req.key()).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "Un fait existe déjà pour " + domain + "/" + req.key() + " (id " + existing.getId() + ")");
        });

        BusinessFact f = new BusinessFact();
        f.setDomain(domain);
        f.setKey(req.key());
        apply(f, req, actor);
        f = facts.save(f);
        audits.save(BusinessFactAudit.of(f, BusinessFactAudit.Action.CREATE, null, req.reason(), actor));
        return f;
    }

    @Transactional
    public BusinessFact update(Long id, BusinessFactRequest req, String actor) {
        requireReason(req.reason());
        BusinessFact f = get(id);
        String domain = normalizeDomain(req.domain());
        validateEnums(domain, req.factType(), req.language());
        if (!f.getDomain().equals(domain) || !f.getKey().equals(req.key())) {
            // (domain, key) is the identity the agent looks facts up by; changing
            // it is a new fact, not an edit, so the trail stays coherent.
            throw new IllegalArgumentException("domain et key ne sont pas modifiables; créez un nouveau fait.");
        }
        String old = f.getValue();
        apply(f, req, actor);
        f = facts.save(f);
        audits.save(BusinessFactAudit.of(f, BusinessFactAudit.Action.UPDATE, old, req.reason(), actor));
        return f;
    }

    @Transactional
    public BusinessFact setActive(Long id, boolean active, String reason, String actor) {
        requireReason(reason);
        BusinessFact f = get(id);
        if (f.isActive() == active) {
            return f;
        }
        f.setActive(active);
        f.setUpdatedBy(actor);
        f = facts.save(f);
        audits.save(BusinessFactAudit.of(f,
                active ? BusinessFactAudit.Action.REACTIVATE : BusinessFactAudit.Action.DEACTIVATE,
                f.getValue(), reason, actor));
        return f;
    }

    private void apply(BusinessFact f, BusinessFactRequest req, String actor) {
        f.setValue(req.value().trim());
        f.setFactType(req.factType());
        f.setTopic(blankToNull(req.topic()));
        f.setLanguage(req.language() == null || req.language().isBlank() ? "fr" : req.language());
        f.setSource(blankToNull(req.source()));
        f.setValidFrom(req.validFrom());
        f.setUpdatedBy(actor);
    }

    private static void validateEnums(String domain, String factType, String language) {
        if (!DOMAINS.contains(domain)) {
            throw new IllegalArgumentException("domain invalide: " + domain + " (attendu: " + DOMAINS + ")");
        }
        if (!FACT_TYPES.contains(factType)) {
            throw new IllegalArgumentException("factType invalide: " + factType + " (attendu: " + FACT_TYPES + ")");
        }
        if (language != null && !language.isBlank() && !LANGUAGES.contains(language)) {
            throw new IllegalArgumentException("language invalide: " + language + " (attendu: " + LANGUAGES + ")");
        }
    }

    private static String normalizeDomain(String domain) {
        return domain == null ? "" : domain.trim().toLowerCase();
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason est obligatoire pour modifier un fait gouverné.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
