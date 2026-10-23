package com.albaraka.backend.services;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/**
 * When the engine has no grounded answer, say the bank's own sentence instead of
 * the engine's.
 *
 * <p>There is one engine now, so the old cross-engine recovery — asking a second
 * pipeline the same question when the first refused — is gone. What survives is the
 * wording rule, which is about the customer, not about engines: RAGLab's refusal is
 * written for an operator ("the supplied documents do not contain…", followed by a
 * list of document ids), and that must never appear on a client's screen. A policy
 * refusal ("I cannot help with that subject") does stay: it is information the
 * customer needs, and replacing it with a knowledge-gap sentence would misreport why
 * the assistant stayed silent.
 */
@Service
public class RefusalHandling {

    /**
     * Refusals that are decisions rather than gaps: the bank's knowledge-gap sentence
     * would misdescribe them, so they are relayed as the engine phrased them.
     */
    private static final Set<String> FINAL_REASONS = Set.of(
            "out_of_scope", "not_offered", "rejected", "pii", "unauthenticated",
            // RAGLab's own vocabulary for "this asks for my balance / my credentials":
            // the answer would have to come from an account, not from a document.
            "private_or_live_request");

    /**
     * Should the edge speak for this refusal instead of relaying the engine's prose?
     *
     * <p>Only for a gap: the engine found no evidence. A decision is left alone.
     */
    public boolean replacesEngineProse(String intention, boolean grounded) {
        return !grounded && isGapRefusal(intention);
    }

    /** Is this refusal a gap rather than a decision? */
    static boolean isGapRefusal(String intention) {
        String reason = MonitoringKpiService.refusalReason(intention);
        return reason == null || !FINAL_REASONS.contains(reason.trim().toLowerCase(Locale.ROOT));
    }
}
