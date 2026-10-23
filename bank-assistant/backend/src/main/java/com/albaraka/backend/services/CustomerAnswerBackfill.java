package com.albaraka.backend.services;

import com.albaraka.backend.model.MessageEntity;
import com.albaraka.backend.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One-off rewrite of stored answers through the same rule the serving path applies.
 *
 * <p>What the bank stores as {@code messages.reponse} is the answer the customer was
 * <em>shown</em> — the controller sanitises before persisting, deliberately, so that the
 * audit row describes the customer's experience rather than the model's draft. Rows
 * written before that policy existed do not obey it: they hold the engine's own wording,
 * which on a refusal has carried the list of documents consulted. Reading them clean at
 * read time (the history endpoints call
 * {@link AnswerSanitizer#sanitizeStored(String)}) stops the leak, but the text stays in
 * the database, in backups and in any clone of them. This closes that.
 *
 * <p>It is Java, not SQL, on purpose: the strip rules are the sanitizer's, and a
 * migration written in SQL would be a second, weaker copy of a policy the application
 * already owns. One policy, one implementation.
 *
 * <p>Off by default, and dry-run by default when on. Rewriting history is a destructive
 * read of the audit trail, so the operator opts in twice:
 * {@code app.chat.history-backfill.enabled=true} reports the count, and adding
 * {@code app.chat.history-backfill.dry-run=false} applies it. The reviewer surfaces
 * ({@code /api/clients/**}, {@code /api/admin/monitoring}) read the same rows without
 * filtering: after this runs, what they show is what a customer was shown — which is the
 * honest meaning of "the answer", and the documents they name come from
 * {@code sources_utilisees} and the trace, untouched.
 */
@Service
public class CustomerAnswerBackfill {

    private static final Logger log = LoggerFactory.getLogger(CustomerAnswerBackfill.class);

    private final MessageRepository messageRepository;
    private final boolean enabled;
    private final boolean dryRun;
    private final int batchSize;

    public CustomerAnswerBackfill(MessageRepository messageRepository,
                                  @Value("${app.chat.history-backfill.enabled:false}") boolean enabled,
                                  @Value("${app.chat.history-backfill.dry-run:true}") boolean dryRun,
                                  @Value("${app.chat.history-backfill.batch-size:500}") int batchSize) {
        this.messageRepository = messageRepository;
        this.enabled = enabled;
        this.dryRun = dryRun;
        this.batchSize = batchSize > 0 ? batchSize : 500;
    }

    /** Runs after the context is up, so a misconfigured flag cannot stop the app from booting. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!enabled) {
            return;
        }
        Result total = new Result(0, 0);
        long cursor = 0L;
        while (true) {
            List<MessageEntity> rows = messageRepository.findRowsForAnswerBackfill(
                    cursor, PageRequest.of(0, batchSize));
            if (rows.isEmpty()) {
                break;
            }
            total = total.plus(apply(rows, dryRun, messageRepository::saveAll));
            cursor = rows.get(rows.size() - 1).getId();
            if (rows.size() < batchSize) {
                break;
            }
        }
        log.info("Customer answer backfill {}: scanned {}, needing rewrite {}",
                dryRun ? "(dry run, nothing written)" : "(applied)", total.scanned(), total.changed());
        if (dryRun && total.changed() > 0) {
            log.info("Re-run with app.chat.history-backfill.dry-run=false to apply it to those {} rows",
                    total.changed());
        }
    }

    /**
     * One batch: replace the stored text where sanitising changes it.
     *
     * <p>Idempotent by construction — {@link AnswerSanitizer#sanitizeStored(String)} on clean
     * text returns the same text — so a run interrupted halfway can simply be re-run, and a
     * second pass over already-cleaned rows writes nothing. Package-visible and free of Spring so
     * the decision (what counts as needing a rewrite) is unit-tested without a database.
     */
    static Result apply(List<MessageEntity> rows, boolean dryRun, Consumer<List<MessageEntity>> saver) {
        List<MessageEntity> dirty = new ArrayList<>();
        int changed = 0;
        for (MessageEntity row : rows) {
            String stored = row.getReponse();
            String clean = AnswerSanitizer.sanitizeStored(stored);
            if (clean == null || clean.equals(stored)) {
                continue;
            }
            changed++;
            if (!dryRun) {
                row.setReponse(clean);
                dirty.add(row);
            }
        }
        if (!dirty.isEmpty()) {
            saver.accept(dirty);
        }
        return new Result(rows.size(), changed);
    }

    /** Rows seen and rows that needed a rewrite — the two numbers an operator acts on. */
    record Result(int scanned, int changed) {
        Result plus(Result other) {
            return new Result(scanned + other.scanned(), changed + other.changed());
        }
    }
}
