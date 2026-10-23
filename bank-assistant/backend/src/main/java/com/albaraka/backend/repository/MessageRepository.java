package com.albaraka.backend.repository;

import com.albaraka.backend.model.MessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    /** Monitoring feed: newest exchanges first (trace included). */
    Page<MessageEntity> findAllByOrderByDateCreationDesc(Pageable pageable);

    /**
     * The live feed, newest first on the real question timestamp. {@code asked_at}
     * is the honest clock for that column (it is stamped when the request enters the
     * backend, before the model answers), and {@code date_creation} keeps older rows
     * — written before that column existed — visible instead of vanishing from the feed.
     */
    @Query("SELECT m FROM MessageEntity m ORDER BY COALESCE(m.askedAt, m.dateCreation) DESC")
    List<MessageEntity> findLiveFeed(Pageable pageable);

    /** Same window, but only exchanges asked after {@code since}: the delta a poller needs. */
    @Query("SELECT m FROM MessageEntity m WHERE COALESCE(m.askedAt, m.dateCreation) > :since"
            + " ORDER BY COALESCE(m.askedAt, m.dateCreation) DESC")
    List<MessageEntity> findLiveFeedSince(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * Keyset pages of the one-off answer backfill (see {@code CustomerAnswerBackfill}):
     * id-ordered so a run is resumable and a batch never shifts under the cursor.
     */
    @Query("SELECT m FROM MessageEntity m WHERE m.id > :after AND m.reponse IS NOT NULL"
            + " ORDER BY m.id ASC")
    List<MessageEntity> findRowsForAnswerBackfill(@Param("after") Long after, Pageable pageable);

    List<MessageEntity> findByConversation_IdOrderByDateCreationAsc(Long conversationId);

    @Query("SELECT m FROM MessageEntity m WHERE m.conversation.sessionId IN (SELECT s.sessionId FROM PublicChatSessionEntity s WHERE s.userId = :userId) ORDER BY m.dateCreation ASC")
    List<MessageEntity> findMessagesByUserIdOrderByDateCreationAsc(@Param("userId") Long userId);

    @Query("SELECT m FROM MessageEntity m WHERE m.conversation.sessionId = :sessionId ORDER BY m.dateCreation ASC")
    List<MessageEntity> findMessagesBySessionIdOrderByDateCreationAsc(@Param("sessionId") String sessionId);

    /**
     * Trouve tous les messages où un document précis apparaît dans les sources utilisées.
     * Utilise l'opérateur PostgreSQL ANY pour interroger le tableau TEXT[].
     */
    @Query(value = "SELECT m.* FROM messages m WHERE :docName = ANY(m.sources_utilisees) ORDER BY m.date_creation DESC",
           countQuery = "SELECT COUNT(*) FROM messages m WHERE :docName = ANY(m.sources_utilisees)",
           nativeQuery = true)
    Page<MessageEntity> findByDocumentSource(@Param("docName") String docName, Pageable pageable);

    @Query("SELECT m.intention, COUNT(m) FROM MessageEntity m GROUP BY m.intention ORDER BY COUNT(m) DESC")
    List<Object[]> countByIntention();

    @Query(value = "SELECT question FROM messages m JOIN conversations c ON m.conversation_id = c.id WHERE c.session_id = :sessionId ORDER BY m.date_creation ASC LIMIT 1", nativeQuery = true)
    String findFirstQuestionBySessionId(@Param("sessionId") String sessionId);
}
