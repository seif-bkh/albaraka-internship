package com.albaraka.backend.services;

import java.util.Locale;

/**
 * What a customer reads when an engine could not ground an answer.
 *
 * <p>The engine's own refusal is not shown, for two reasons. It describes the
 * pipeline rather than the bank — "I cannot answer from the supplied documents, I
 * have no access to accounts, credentials or live data" tells a customer how the
 * retrieval layer works and mentions credentials nobody asked about — and it is the
 * one place a resource list has ever arrived on a customer screen, because an
 * engine that found <em>something</em> but not enough tends to append what it
 * looked at. Replacing the text removes both problems at the source instead of
 * filtering a sentence that should never have been written for a customer.
 *
 * <p>The wording mirrors {@code _TEXTS["gap"]} in {@code albaraka/server/orchestrate.py}:
 * the in-house pipeline already says exactly this, in the customer's language, so a
 * refusal reads the same whichever engine served — and the agent copy stays the one
 * to edit, this is the edge repeating it because it must answer before the agent's
 * voice reaches it. Keep the two in step.
 */
public final class RefusalMessage {

    /** Same promise in three languages, plus the French default (AGENTS.md: no half-translated states). */
    private static final String FRENCH =
            "Je n'ai pas encore d'information fiable sur ce sujet dans mes documents. "
                    + "Pour cette demande, contactez directement votre agence Al Baraka.";
    private static final String ARABIC =
            "لا تتوفر لديّ بعد معلومات موثوقة حول هذا الموضوع في وثائقي. "
                    + "يرجى الاتصال مباشرة بفرع بنك البركة.";
    private static final String ENGLISH =
            "I do not have reliable information on this in my documents yet. "
                    + "For this request, please contact your Al Baraka branch directly.";

    private RefusalMessage() {
    }

    /**
     * The refusal for this request language. Anything we cannot recognise is French:
     * the bank's working language, and the same default the agent uses.
     */
    public static String forLanguage(String language) {
        if (language == null || language.isBlank()) {
            return FRENCH;
        }
        String code = language.trim().toLowerCase(Locale.ROOT);
        if (code.startsWith("ar")) {
            return ARABIC;
        }
        if (code.startsWith("en")) {
            return ENGLISH;
        }
        return FRENCH;
    }
}
