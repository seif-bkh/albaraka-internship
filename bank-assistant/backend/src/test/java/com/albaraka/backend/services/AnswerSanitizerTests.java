package com.albaraka.backend.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a customer must never read: where the answer came from. Every rule here is
 * a shape a model has actually produced in this project's own history — bracketed
 * markers, "(Source : …)" in three languages, a file name, a link list at the end.
 *
 * <p>The second half matters as much as the first: an answer with no provenance in
 * it must come out untouched, because the cheaper way to pass the first half is to
 * strip everything.
 */
class AnswerSanitizerTests {

    @Test
    void removesBracketedMarkersAndInlineSourceMentions() {
        assertEquals("Taux 4,75 % et frais 300 DT",
                AnswerSanitizer.sanitize("Taux 4,75 % [S1] et frais 300 DT (Source : grille.pdf)"));
        assertEquals("Le Mourabaha est un financement adossé à un achat réel.",
                AnswerSanitizer.sanitize("Le Mourabaha est un financement adossé à un achat réel [S1]. "
                        + "(Source : Guide interne des opérations bancaires islamiques)"));
    }

    @Test
    void removesMarkersSpanningSeveralDocuments() {
        assertEquals("Deux produits.", AnswerSanitizer.sanitize("Deux produits. [S1, S2]"));
        assertEquals("Deux produits.", AnswerSanitizer.sanitize("Deux produits. [doc_12]"));
        assertEquals("Deux produits.", AnswerSanitizer.sanitize("Deux produits. [source: grille]"));
    }

    @Test
    void removesArabicSourceMentions() {
        assertEquals("التكلفة 300 دينار",
                AnswerSanitizer.sanitize("التكلفة 300 دينار [S2] (المصدر: دورية 19-08)"));
    }

    @Test
    void dropsTrailingSourceSection() {
        String answer = """
                Le Mourabaha couvre l'automobile.

                Sources:
                - Guide_Interne_Operations_Bancaires_Islamiques.docx
                - Circulaire_BCT_2019-08.pdf""";
        assertEquals("Le Mourabaha couvre l'automobile.", AnswerSanitizer.sanitize(answer));
    }

    @Test
    void dropsTrailingLinkListEvenWithoutAHeading() {
        String answer = """
                Vous pouvez commencer ici.
                - https://example.org/mourabaha
                - https://example.org/agences""";
        assertEquals("Vous pouvez commencer ici.", AnswerSanitizer.sanitize(answer));
    }

    @Test
    void keepsProseThatMerelyFollowsTheWordSources() {
        // A "Sources:" line followed by a long sentence is the assistant talking; the
        // answer is worth more than the risk of leaving one mention visible.
        String answer = "Réponse utile.\nSources: nous restons disponibles pour toute question sur nos "
                + "financements et nos agences, partout en Tunisie, du lundi au vendredi de 8h30 à 16h30, "
                + "et nous pouvons aussi vous orienter vers un conseiller dédié qui étudiera votre dossier "
                + "de financement avec vous dans les meilleurs délais habituels de la banque.";
        assertEquals(answer, AnswerSanitizer.sanitize(answer));
    }

    @Test
    void keepsMarkdownLinkLabelButDropsItsTarget() {
        assertEquals("Voir le site pour la suite.",
                AnswerSanitizer.sanitize("Voir [le site](https://albaraka.com.tn) pour la suite."));
    }

    @Test
    void removesInternalLinksAndDocumentFileNames() {
        assertEquals("Contactez le service pour le détail.",
                AnswerSanitizer.sanitize("Contactez http://minio:9000/bucket/guide.pdf le service "
                        + "documents/Loi_2016-48.pdf pour le détail."));
        // The sentence around the file name is prose and stays; the file name goes.
        assertEquals("Détail dans", AnswerSanitizer.sanitize("Détail dans Guide_Interne.docx"));
    }

    @Test
    void leavesACleanAnswerAlone() {
        String answer = "Le compte de dépôt est ouvert sans frais. Les horaires d'agence sont "
                + "8h30 – 16h30 du lundi au vendredi.";
        assertEquals(answer, AnswerSanitizer.sanitize(answer));
    }

    @Test
    void aProvenanceFreeAnswerIsReturnedCharacterForCharacter() {
        // The cheap way to pass every rule above is to strip everything; this is the
        // counterweight — an answer with no provenance must come back untouched.
        String answer = "Deux étapes : préparer la CIN, puis signer le contrat en agence. "
                + "Le délai est de 5 jours ouvrés, hors délai de rétractation de 10 jours.";
        assertEquals(answer, AnswerSanitizer.sanitize(answer));
        // …and the repair that follows a removal must not reach further than the hole:
        // French puts a space before ": ; ! ?", so a citation taken out of a sentence
        // may not drag the typography of the rest of it down with it.
        assertEquals("Deux étapes : préparer la CIN.",
                AnswerSanitizer.sanitize("Deux étapes : préparer la CIN (Source : Guide_interne.docx)."));
    }

    @Test
    void keepsArabicProductNamesInParentheses() {
        String answer = "Le financement Mourabaha (المرابحة) couvre l'automobile et l'équipement.";
        assertEquals(answer, AnswerSanitizer.sanitize(answer));
    }

    @Test
    void nullAndBlankInputsPassThrough() {
        assertEquals(null, AnswerSanitizer.sanitize(null));
        assertEquals("   ", AnswerSanitizer.sanitize("   "));
    }

    @Test
    void streamingRelaysTextButNeverASplitCitationMarker() {
        AnswerSanitizer.Stream stream = new AnswerSanitizer.Stream();
        StringBuilder shown = new StringBuilder();
        // A marker split across three deltas must never appear in what is relayed.
        for (String delta : new String[]{
                "Le taux applicable est de 4,75 pour cent ",
                "de la valeur finan[cée",
                " selon le barème. [S",
                "1] (Source : gui",
                "de_interne.docx)"}) {
            shown.append(stream.accept(delta));
        }
        shown.append(stream.flush());
        String out = shown.toString();
        assertFalse(out.contains("[S1]"), out);
        assertFalse(out.contains("guide_interne.docx"), out);
        assertTrue(out.contains("4,75 pour cent"), out);
    }

    @Test
    void streamingForwardsProseWhileItStreams() {
        AnswerSanitizer.Stream stream = new AnswerSanitizer.Stream();
        StringBuilder shown = new StringBuilder();
        String prose = "Le financement Ijara permet de louer un bien avec promesse d'achat en fin de contrat. ";
        for (int i = 0; i < prose.length(); i += 12) {
            shown.append(stream.accept(prose.substring(i, Math.min(prose.length(), i + 12))));
        }
        shown.append(stream.flush());
        assertEquals(prose.strip(), shown.toString().strip());
    }

    @Test
    void aStoredRefusalIsCleanWhenItIsReplayed() {
        // Rows written before the no-provenance policy are still in the database, and the
        // history endpoints read them back: replaying must not re-open the leak.
        String stored = "لا أستطيع الإجابة اعتمادا على المستندات المقدمة.\n\n"
                + "الموارد:\n1. دليل التمويل\n2. دورية 2016-08";
        String once = AnswerSanitizer.sanitizeStored(stored);
        assertEquals("لا أستطيع الإجابة اعتمادا على المستندات المقدمة.", once);
        // …and reading it a second time must not start eating the answer itself.
        assertEquals(once, AnswerSanitizer.sanitizeStored(once));
        assertEquals("Un texte propre reste identique.",
                AnswerSanitizer.sanitizeStored("Un texte propre reste identique."));
        assertNull(AnswerSanitizer.sanitizeStored(null));
    }

    // A retrieval service answers a question it cannot ground by saying so and
    // appending what it read. The label is rarely "Sources", and the refusal sentence
    // is where the vocabulary of the pipeline reaches a customer, so the block rules
    // cover the words those services actually use — in Arabic as in French.
    @Test
    void removesAResourceListAppendedAfterARefusal() {
        assertEquals("لا أستطيع الإجابة اعتمادا على المستندات المقدمة.",
                AnswerSanitizer.sanitize("لا أستطيع الإجابة اعتمادا على المستندات المقدمة.\n\n"
                        + "الموارد:\n1. دليل التمويل لدى البنك\n2. دورية 2016-08"));
        assertEquals("Je n'ai pas d'information fiable sur ce point.",
                AnswerSanitizer.sanitize("Je n'ai pas d'information fiable sur ce point.\n"
                        + "Documents consultés :\n- Le guide interne\n- La circulaire"));
        assertTrue(AnswerSanitizer.sanitize(
                "Resources:\n- Guide des financements\n- Circulaire 2019-08").isBlank());
        assertEquals("رد مختصر.",
                AnswerSanitizer.sanitize("رد مختصر.\nالوثائق المستخدمة\n1. دليل\n2. دورية"));
    }

    @Test
    void removesNumberedReferenceMentionsWithoutAColon() {
        assertEquals("Le Mourabaha est un achat réel.",
                AnswerSanitizer.sanitize("Le Mourabaha est un achat réel [réf. 3]."));
        assertEquals("Le Mourabaha est un achat réel.",
                AnswerSanitizer.sanitize("Le Mourabaha est un achat réel (réf. 3)."));
        assertEquals("Taux de 4,75 pour cent.",
                AnswerSanitizer.sanitize("Taux de 4,75 pour cent (source 12)."));
        assertEquals("Taux de 4,75 pour cent.",
                AnswerSanitizer.sanitize("Taux de 4,75 pour cent [Resources: 2]."));
    }

    @Test
    void aWidenedVocabularyMustNotEatTheAnswerItself() {
        // "Required documents" is the answer to a paperwork question, not a citation —
        // the labels that strip are the ones that name what was read, not what to file.
        String fr = "Documents requis : la CIN, trois bulletins de salaire et une facture proforma "
                + "de moins de trois mois.";
        assertEquals(fr, AnswerSanitizer.sanitize(fr));
        String ar = "الوثائق المطلوبة: نسخة من بطاقة التعريف الوطنية وثلاثة مطولات أجر.";
        assertEquals(ar, AnswerSanitizer.sanitize(ar));
        String ar2 = "المستندات الواجب تقديمها: طلب كتابي ومعرّف جبائي.";
        assertEquals(ar2, AnswerSanitizer.sanitize(ar2));
        // A number in parentheses is a clarification, not a reference.
        assertEquals("Délai de 3 (deux) mois selon le dossier.",
                AnswerSanitizer.sanitize("Délai de 3 (deux) mois selon le dossier."));
    }
}
