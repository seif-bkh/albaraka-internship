package com.albaraka.backend.services;

import com.albaraka.backend.model.MessageEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one-off rewrite of stored answers, tested on the decision rather than on the
 * database: which rows count as still carrying provenance, and what a dry run must not
 * do. The dry-run guarantee is the one worth a test — an operator flipping
 * {@code enabled=true} to see the size of the job must not get the job done.
 */
class CustomerAnswerBackfillTests {

    private static MessageEntity stored(String reponse) {
        MessageEntity message = new MessageEntity();
        message.setReponse(reponse);
        return message;
    }

    @Test
    void aDryRunCountsAndChangesNothing() {
        List<MessageEntity> rows = List.of(
                stored("Réponse.\nSources:\n- Guide_interne_Operations_Bancaires_Islamiques.docx"),
                stored("Réponse déjà propre."));

        CustomerAnswerBackfill.Result result = CustomerAnswerBackfill.apply(rows, true, dirty -> {
            throw new IllegalStateException("a dry run must not save anything");
        });

        assertEquals(2, result.scanned());
        assertEquals(1, result.changed());
        assertEquals("Réponse.\nSources:\n- Guide_interne_Operations_Bancaires_Islamiques.docx",
                rows.get(0).getReponse());
    }

    @Test
    void anAppliedRunRewritesOnlyWhatCarriesProvenance() {
        List<MessageEntity> rows = new ArrayList<>(List.of(
                stored("Réponse.\nSources:\n- guide_interne.docx"),
                stored("Réponse déjà propre."),
                stored("لا أستطيع الإجابة.\n\nالموارد:\n1. دليل التمويل")));
        List<List<MessageEntity>> saved = new ArrayList<>();

        CustomerAnswerBackfill.Result result = CustomerAnswerBackfill.apply(rows, false, saved::add);

        assertEquals(3, result.scanned());
        assertEquals(2, result.changed());
        assertEquals("Réponse.", rows.get(0).getReponse());
        assertEquals("Réponse déjà propre.", rows.get(1).getReponse());
        assertEquals("لا أستطيع الإجابة.", rows.get(2).getReponse());
        assertEquals(1, saved.size(), "one write per batch, not one per row");
        assertEquals(2, saved.get(0).size());
    }

    @Test
    void aSecondPassWritesNothingBecauseTheRuleIsIdempotent() {
        List<MessageEntity> rows = new ArrayList<>(List.of(stored("Réponse.\n\nResources:\n- a guide")));
        List<List<MessageEntity>> saved = new ArrayList<>();

        CustomerAnswerBackfill.apply(rows, false, saved::add);
        CustomerAnswerBackfill.Result second = CustomerAnswerBackfill.apply(rows, false, saved::add);

        assertEquals(0, second.changed());
        assertEquals(1, saved.size());
        assertEquals("Réponse.", rows.get(0).getReponse());
    }

    @Test
    void anAbsentOrUnchangedAnswerIsNotTreatedAsDirty() {
        List<MessageEntity> rows = List.of(stored(null), stored("   "), stored("Une phrase nette."));
        CustomerAnswerBackfill.Result result = CustomerAnswerBackfill.apply(rows, true, dirty ->
                assertEquals(0, dirty.size(), "nothing to save here"));
        assertEquals(3, result.scanned());
        assertEquals(0, result.changed());
    }
}
