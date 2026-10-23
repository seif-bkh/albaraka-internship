package com.albaraka.backend.services;

import com.albaraka.backend.dto.AdvisorRequestRequest;
import com.albaraka.backend.model.AdvisorRequest;
import com.albaraka.backend.model.BusinessFact;
import com.albaraka.backend.repository.AdvisorRequestRepository;
import com.albaraka.backend.repository.BusinessFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Portal "advisor" flows (P0): APPOINTMENT (rendez-vous) and CALLBACK (rappel).
 *
 * <p>Requests are persisted as EN_ATTENTE — the bank confirms by phone (24 h
 * notice pattern, docs/UIUX_RESEARCH.md §2). Branch is free text: no branch
 * catalogue exists in governed data yet (P1 deferred).
 */
@Service
public class AdvisorRequestService {

    private static final String CONTACT_DOMAIN = "service";
    private static final String CONTACT_FACT_TYPE = "contact";

    /** Minimum notice: the earliest bookable day is tomorrow. */
    static final long MIN_NOTICE_DAYS = 1;
    /** Booking horizon. */
    static final long MAX_HORIZON_DAYS = 60;

    private final AdvisorRequestRepository requests;
    private final BusinessFactRepository facts;

    public AdvisorRequestService(AdvisorRequestRepository requests, BusinessFactRepository facts) {
        this.requests = requests;
        this.facts = facts;
    }

    @Transactional
    public AdvisorRequest create(Long clientId, AdvisorRequestRequest req) {
        AdvisorRequest r = new AdvisorRequest();
        r.setClientId(clientId);
        AdvisorRequest.Kind kind = AdvisorRequest.Kind.valueOf(req.kind());
        r.setKind(kind);

        if (kind == AdvisorRequest.Kind.APPOINTMENT) {
            requireText(req.motif(), "Le motif du rendez-vous est requis.");
            requireText(req.meetingMethod(), "Choisissez un mode de rencontre (agence ou téléphone).");
            if (req.preferredDate() == null) {
                throw new IllegalArgumentException("Choisissez une date.");
            }
            requireText(req.preferredTime(), "Choisissez un horaire.");
            LocalDate today = LocalDate.now();
            if (req.preferredDate().isBefore(today.plusDays(MIN_NOTICE_DAYS))
                    || req.preferredDate().isAfter(today.plusDays(MAX_HORIZON_DAYS))) {
                throw new IllegalArgumentException("La date doit être entre demain et 60 jours.");
            }
            r.setMotif(req.motif().trim());
            r.setMeetingMethod(req.meetingMethod().trim());
            r.setPreferredBranch(trimOrNull(req.preferredBranch()));
            r.setPreferredDate(req.preferredDate());
            r.setPreferredTime(req.preferredTime().trim());
            r.setMessage(trimOrNull(req.message()));
        } else { // CALLBACK
            requireText(req.telephone(), "Le numéro de téléphone est requis pour un rappel.");
            r.setTelephone(req.telephone().trim());
            r.setPreferredTime(trimOrNull(req.preferredTime()));
            r.setMessage(trimOrNull(req.message()));
        }
        return requests.save(r);
    }

    /** Active contact facts (service domain) shown in the advisor dialog. */
    @Transactional(readOnly = true)
    public List<String> contacts() {
        return facts.findByDomainAndFactTypeAndActiveTrueOrderByKeyAsc(CONTACT_DOMAIN, CONTACT_FACT_TYPE)
                .stream()
                .map(BusinessFact::getValue)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
